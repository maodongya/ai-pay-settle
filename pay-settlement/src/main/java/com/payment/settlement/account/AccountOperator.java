package com.payment.settlement.account; // 结算账户操作包

import com.payment.common.cache.RedisKeys;
import com.payment.common.enums.AccountFlowOpType; // 账户流水操作类型枚举
import com.payment.common.exception.BizException; // 业务异常
import com.payment.common.exception.ErrorCode; // 错误码
import com.payment.common.redis.RedisDistributedLock;
import com.payment.domain.entity.AccountFlowEntity; // 账户流水实体
import com.payment.domain.entity.MerchantSettleAccountEntity; // 商户结算账户实体
import com.payment.domain.repository.AccountFlowRepository; // 账户流水仓储
import com.payment.domain.repository.MerchantSettleAccountRepository; // 商户结算账户仓储
import org.springframework.stereotype.Component; // Spring 组件注解
import org.springframework.transaction.annotation.Transactional; // 事务注解
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.math.BigDecimal; // 高精度数值
import java.time.Duration;
import java.time.LocalDateTime; // 本地日期时间
import java.util.function.Supplier;

/**
 * 账户操作组件，封装余额入账、冻结、解冻、扣款等原子操作。
 */
@Component // 注册为 Spring 组件
public class AccountOperator {

    private static final int MAX_RETRY = 3; // 乐观锁最大重试次数
    private static final Duration SETTLE_LOCK_TTL = Duration.ofSeconds(30);

    private final MerchantSettleAccountRepository accountRepository; // 商户结算账户仓储
    private final AccountFlowRepository accountFlowRepository; // 账户流水仓储
    private final RedisDistributedLock distributedLock;

    /**
     * 构造注入依赖。
     */
    public AccountOperator(MerchantSettleAccountRepository accountRepository,
                           AccountFlowRepository accountFlowRepository,
                           RedisDistributedLock distributedLock) {
        this.accountRepository = accountRepository; // 赋值账户仓储
        this.accountFlowRepository = accountFlowRepository; // 赋值流水仓储
        this.distributedLock = distributedLock;
    }

    /**
     * 商户账户入账，支持幂等。
     */
    @Transactional // 开启事务
    public MerchantSettleAccountEntity credit(Long merchantId, String billNo, BigDecimal amount, AccountFlowOpType opType) {
        return withSettleLock(merchantId, () -> {
            if (accountFlowRepository.existsByBillNoAndOpType(billNo, opType.getCode())) { // 流水已存在
                return accountRepository.findByMerchantId(merchantId).orElseThrow(); // 幂等返回当前账户
            }
            return updateBalance(merchantId, billNo, null, amount, opType, true); // 更新余额
        });
    }

    /**
     * 冻结商户待结算余额。
     */
    @Transactional // 开启事务
    public MerchantSettleAccountEntity freeze(Long merchantId, String settleNo, BigDecimal amount) {
        return withSettleLock(merchantId,
                () -> updateBalance(merchantId, null, settleNo, amount.negate(), AccountFlowOpType.FREEZE, false, amount));
    }

    /**
     * 解冻商户冻结余额。
     */
    @Transactional // 开启事务
    public MerchantSettleAccountEntity unfreeze(Long merchantId, String settleNo, BigDecimal amount) {
        return withSettleLock(merchantId,
                () -> updateBalance(merchantId, null, settleNo, amount, AccountFlowOpType.UNFREEZE, false, amount.negate()));
    }

    /**
     * 从冻结余额中扣减（支付成功）。
     */
    @Transactional // 开启事务
    public MerchantSettleAccountEntity deductFrozen(Long merchantId, String settleNo, BigDecimal amount) {
        return withSettleLock(merchantId,
                () -> updateFrozenOnly(merchantId, settleNo, amount.negate(), AccountFlowOpType.DEDUCT));
    }

    /**
     * 退款扣减待结算余额，支持幂等。
     */
    @Transactional // 开启事务
    public MerchantSettleAccountEntity debit(Long merchantId, String billNo, BigDecimal amount) {
        return withSettleLock(merchantId, () -> {
            if (accountFlowRepository.existsByBillNoAndOpType(billNo, AccountFlowOpType.REFUND_DEBIT.getCode())) {
                return accountRepository.findByMerchantId(merchantId).orElseThrow();
            }
            return updateBalance(merchantId, billNo, null, amount.negate(), AccountFlowOpType.REFUND_DEBIT, true);
        });
    }

    private MerchantSettleAccountEntity withSettleLock(Long merchantId, Supplier<MerchantSettleAccountEntity> action) {
        String lockKey = RedisKeys.settleLock(merchantId);
        String token = tryAcquireSettleLock(lockKey);
        if (token == null) {
            throw BizException.of(ErrorCode.CONCURRENT_UPDATE);
        }
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
                @Override
                public void afterCompletion(int status) {
                    distributedLock.unlock(lockKey, token);
                }
            });
            return action.get();
        }
        try {
            return action.get();
        } finally {
            distributedLock.unlock(lockKey, token);
        }
    }

    private String tryAcquireSettleLock(String lockKey) {
        for (int i = 0; i < MAX_RETRY; i++) {
            String token = distributedLock.tryLock(lockKey, SETTLE_LOCK_TTL);
            if (token != null) {
                return token;
            }
            try {
                Thread.sleep(50L * (i + 1));
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return null;
            }
        }
        return null;
    }

    /**
     * 更新待结算余额（冻结变动为零）。
     */
    private MerchantSettleAccountEntity updateBalance(Long merchantId, String billNo, String settleNo,
                                                     BigDecimal waitDelta, AccountFlowOpType opType, boolean checkDuplicate) {
        return updateBalance(merchantId, billNo, settleNo, waitDelta, opType, checkDuplicate, BigDecimal.ZERO); // 委托完整方法
    }

    /**
     * 更新待结算余额和冻结余额，带乐观锁重试。
     */
    private MerchantSettleAccountEntity updateBalance(Long merchantId, String billNo, String settleNo,
                                                     BigDecimal waitDelta, AccountFlowOpType opType,
                                                     boolean checkDuplicate, BigDecimal frozenDelta) {
        for (int i = 0; i < MAX_RETRY; i++) { // 乐观锁重试
            MerchantSettleAccountEntity account = accountRepository.findByMerchantId(merchantId) // 查询账户
                    .orElseThrow(() -> BizException.of(ErrorCode.MERCHANT_INVALID)); // 商户不存在
            BigDecimal before = account.waitBalance; // 变更前余额
            BigDecimal after = before.add(waitDelta); // 变更后余额
            if (after.compareTo(BigDecimal.ZERO) < 0) { // 余额不足
                throw BizException.of(ErrorCode.INSUFFICIENT_BALANCE);
            }
            BigDecimal frozenAfter = account.frozenBalance.add(frozenDelta); // 变更后冻结余额
            if (frozenAfter.compareTo(BigDecimal.ZERO) < 0) { // 冻结余额不足
                throw BizException.of(ErrorCode.INSUFFICIENT_BALANCE);
            }
            account.waitBalance = after; // 更新待结算余额
            account.frozenBalance = frozenAfter; // 更新冻结余额
            try { // 保存并记录流水
                MerchantSettleAccountEntity saved = accountRepository.save(account); // 保存账户
                AccountFlowEntity flow = new AccountFlowEntity(); // 创建流水
                flow.merchantId = merchantId; // 商户 ID
                flow.billNo = billNo; // 账单号
                flow.settleNo = settleNo; // 结算单号
                flow.opType = opType.getCode(); // 操作类型
                flow.amount = waitDelta.abs().max(frozenDelta.abs()); // 流水金额
                flow.beforeBalance = before; // 变更前余额
                flow.afterBalance = after; // 变更后余额
                flow.createTime = LocalDateTime.now(); // 创建时间
                accountFlowRepository.save(flow); // 保存流水
                return saved; // 返回更新后账户
            } catch (Exception e) { // 并发冲突
                if (i == MAX_RETRY - 1) { // 最后一次重试
                    throw BizException.of(ErrorCode.CONCURRENT_UPDATE); // 抛出并发更新异常
                }
            }
        }
        throw BizException.of(ErrorCode.CONCURRENT_UPDATE); // 重试耗尽
    }

    /**
     * 仅更新冻结余额，带乐观锁重试。
     */
    private MerchantSettleAccountEntity updateFrozenOnly(Long merchantId, String settleNo,
                                                         BigDecimal frozenDelta, AccountFlowOpType opType) {
        for (int i = 0; i < MAX_RETRY; i++) { // 乐观锁重试
            MerchantSettleAccountEntity account = accountRepository.findByMerchantId(merchantId) // 查询账户
                    .orElseThrow(() -> BizException.of(ErrorCode.MERCHANT_INVALID)); // 商户不存在
            BigDecimal frozenAfter = account.frozenBalance.add(frozenDelta); // 变更后冻结余额
            if (frozenAfter.compareTo(BigDecimal.ZERO) < 0) { // 冻结余额不足
                throw BizException.of(ErrorCode.INSUFFICIENT_BALANCE);
            }
            account.frozenBalance = frozenAfter; // 更新冻结余额
            try { // 保存并记录流水
                MerchantSettleAccountEntity saved = accountRepository.save(account); // 保存账户
                AccountFlowEntity flow = new AccountFlowEntity(); // 创建流水
                flow.merchantId = merchantId; // 商户 ID
                flow.settleNo = settleNo; // 结算单号
                flow.opType = opType.getCode(); // 操作类型
                flow.amount = frozenDelta.abs(); // 流水金额
                flow.beforeBalance = account.waitBalance; // 变更前待结算余额
                flow.afterBalance = account.waitBalance; // 变更后待结算余额不变
                flow.createTime = LocalDateTime.now(); // 创建时间
                accountFlowRepository.save(flow); // 保存流水
                return saved; // 返回更新后账户
            } catch (Exception e) { // 并发冲突
                if (i == MAX_RETRY - 1) { // 最后一次重试
                    throw BizException.of(ErrorCode.CONCURRENT_UPDATE); // 抛出并发更新异常
                }
            }
        }
        throw BizException.of(ErrorCode.CONCURRENT_UPDATE); // 重试耗尽
    }
}
