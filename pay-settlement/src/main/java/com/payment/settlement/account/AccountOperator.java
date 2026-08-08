package com.payment.settlement.account;

import com.payment.common.cache.RedisKeys;
import com.payment.common.enums.AccountFlowOpType;
import com.payment.common.exception.BizException;
import com.payment.common.exception.ErrorCode;
import com.payment.common.redis.RedisDistributedLock;
import com.payment.domain.entity.AccountFlowEntity;
import com.payment.domain.entity.MerchantSettleAccountEntity;
import com.payment.domain.repository.AccountFlowRepository;
import com.payment.domain.repository.MerchantSettleAccountRepository;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.function.Supplier;

/**
 * 账户操作组件，封装余额入账、冻结、解冻、扣款等原子操作。
 */
@Component
public class AccountOperator {

    private static final int MAX_RETRY = 3;
    private static final Duration SETTLE_LOCK_TTL = Duration.ofSeconds(30);

    private final MerchantSettleAccountRepository accountRepository;
    private final AccountFlowRepository accountFlowRepository;
    private final RedisDistributedLock distributedLock;

    /**
     * 构造注入账户仓储、流水仓储与分布式锁。
     */
    public AccountOperator(MerchantSettleAccountRepository accountRepository,
                           AccountFlowRepository accountFlowRepository,
                           RedisDistributedLock distributedLock) {
        this.accountRepository = accountRepository;
        this.accountFlowRepository = accountFlowRepository;
        this.distributedLock = distributedLock;
    }

    /**
     * 商户入账：增加待结算余额并记录流水，幂等按 billNo+opType 去重。
     */
    @Transactional
    public MerchantSettleAccountEntity credit(Long merchantId, String billNo, BigDecimal amount, AccountFlowOpType opType) {
        return withSettleLock(merchantId, () -> {
            if (accountFlowRepository.existsByBillNoAndOpType(billNo, opType.getCode())) {
                return accountRepository.findByMerchantId(merchantId).orElseThrow();
            }
            return updateBalance(merchantId, billNo, null, amount, opType, BigDecimal.ZERO);
        });
    }

    /**
     * 冻结余额：从待结算余额转入冻结余额，用于提现/结算预扣。
     */
    @Transactional
    public MerchantSettleAccountEntity freeze(Long merchantId, String settleNo, BigDecimal amount) {
        return withSettleLock(merchantId, () -> {
            if (accountFlowRepository.existsBySettleNoAndOpType(settleNo, AccountFlowOpType.FREEZE.getCode())) {
                return accountRepository.findByMerchantId(merchantId).orElseThrow();
            }
            return updateBalance(merchantId, null, settleNo, amount.negate(), AccountFlowOpType.FREEZE, amount);
        });
    }

    /**
     * 解冻余额：将冻结金额退回待结算余额（如提现失败回滚）。
     */
    @Transactional
    public MerchantSettleAccountEntity unfreeze(Long merchantId, String settleNo, BigDecimal amount) {
        return withSettleLock(merchantId, () -> {
            if (accountFlowRepository.existsBySettleNoAndOpType(settleNo, AccountFlowOpType.UNFREEZE.getCode())) {
                return accountRepository.findByMerchantId(merchantId).orElseThrow();
            }
            return updateBalance(merchantId, null, settleNo, amount, AccountFlowOpType.UNFREEZE, amount.negate());
        });
    }

    /**
     * 扣减冻结余额：结算打款成功后从冻结中扣除，不改动待结算余额。
     */
    @Transactional
    public MerchantSettleAccountEntity deductFrozen(Long merchantId, String settleNo, BigDecimal amount) {
        return withSettleLock(merchantId, () -> {
            if (accountFlowRepository.existsBySettleNoAndOpType(settleNo, AccountFlowOpType.DEDUCT.getCode())) {
                return accountRepository.findByMerchantId(merchantId).orElseThrow();
            }
            return updateFrozenOnly(merchantId, settleNo, amount.negate(), AccountFlowOpType.DEDUCT);
        });
    }

    /**
     * 退款扣减：从待结算余额借记，幂等按 billNo+REFUND_DEBIT 去重。
     */
    @Transactional
    public MerchantSettleAccountEntity debit(Long merchantId, String billNo, BigDecimal amount) {
        return withSettleLock(merchantId, () -> {
            if (accountFlowRepository.existsByBillNoAndOpType(billNo, AccountFlowOpType.REFUND_DEBIT.getCode())) {
                return accountRepository.findByMerchantId(merchantId).orElseThrow();
            }
            return updateBalance(merchantId, billNo, null, amount.negate(), AccountFlowOpType.REFUND_DEBIT, BigDecimal.ZERO);
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

    private MerchantSettleAccountEntity updateBalance(Long merchantId, String billNo, String settleNo,
                                                     BigDecimal waitDelta, AccountFlowOpType opType,
                                                     BigDecimal frozenDelta) {
        LocalDateTime now = LocalDateTime.now();
        for (int i = 0; i < MAX_RETRY; i++) {
            MerchantSettleAccountEntity account = accountRepository.findByMerchantId(merchantId)
                    .orElseThrow(() -> BizException.of(ErrorCode.MERCHANT_INVALID));
            BigDecimal before = account.waitBalance;
            BigDecimal after = before.add(waitDelta);
            if (after.compareTo(BigDecimal.ZERO) < 0) {
                throw BizException.of(ErrorCode.INSUFFICIENT_BALANCE);
            }
            BigDecimal frozenAfter = account.frozenBalance.add(frozenDelta);
            if (frozenAfter.compareTo(BigDecimal.ZERO) < 0) {
                throw BizException.of(ErrorCode.INSUFFICIENT_BALANCE);
            }
            if (!accountRepository.updateBalanceCas(merchantId, waitDelta, frozenDelta, account.version, now)) {
                if (i == MAX_RETRY - 1) {
                    throw BizException.of(ErrorCode.CONCURRENT_UPDATE);
                }
                continue;
            }
            account.waitBalance = after;
            account.frozenBalance = frozenAfter;
            account.version = account.version + 1;
            saveFlow(merchantId, billNo, settleNo, opType, waitDelta.abs().max(frozenDelta.abs()), before, after);
            return account;
        }
        throw BizException.of(ErrorCode.CONCURRENT_UPDATE);
    }

    private MerchantSettleAccountEntity updateFrozenOnly(Long merchantId, String settleNo,
                                                         BigDecimal frozenDelta, AccountFlowOpType opType) {
        LocalDateTime now = LocalDateTime.now();
        for (int i = 0; i < MAX_RETRY; i++) {
            MerchantSettleAccountEntity account = accountRepository.findByMerchantId(merchantId)
                    .orElseThrow(() -> BizException.of(ErrorCode.MERCHANT_INVALID));
            BigDecimal frozenAfter = account.frozenBalance.add(frozenDelta);
            if (frozenAfter.compareTo(BigDecimal.ZERO) < 0) {
                throw BizException.of(ErrorCode.INSUFFICIENT_BALANCE);
            }
            if (!accountRepository.updateFrozenCas(merchantId, frozenDelta, account.version, now)) {
                if (i == MAX_RETRY - 1) {
                    throw BizException.of(ErrorCode.CONCURRENT_UPDATE);
                }
                continue;
            }
            account.frozenBalance = frozenAfter;
            account.version = account.version + 1;
            saveFlow(merchantId, null, settleNo, opType, frozenDelta.abs(),
                    account.waitBalance, account.waitBalance);
            return account;
        }
        throw BizException.of(ErrorCode.CONCURRENT_UPDATE);
    }

    private void saveFlow(Long merchantId, String billNo, String settleNo, AccountFlowOpType opType,
                          BigDecimal amount, BigDecimal beforeBalance, BigDecimal afterBalance) {
        AccountFlowEntity flow = new AccountFlowEntity();
        flow.merchantId = merchantId;
        flow.billNo = billNo;
        flow.settleNo = settleNo;
        flow.opType = opType.getCode();
        flow.amount = amount;
        flow.beforeBalance = beforeBalance;
        flow.afterBalance = afterBalance;
        flow.createTime = LocalDateTime.now();
        accountFlowRepository.save(flow);
    }
}
