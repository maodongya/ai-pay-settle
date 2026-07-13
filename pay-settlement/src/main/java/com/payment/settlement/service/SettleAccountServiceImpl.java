package com.payment.settlement.service; // 结算服务包

import com.payment.api.dto.*; // 结算相关 DTO
import com.payment.api.service.SettleAccountService; // 结算账户服务接口
import com.payment.common.enums.AccountFlowOpType; // 账户流水操作类型枚举
import com.payment.common.enums.SettleMode; // 结算模式枚举
import com.payment.common.enums.SettleOrderStatus; // 结算订单状态枚举
import com.payment.common.exception.BizException; // 业务异常
import com.payment.common.exception.ErrorCode; // 错误码
import com.payment.common.util.SeqGenerator; // 序列号生成器
import com.payment.domain.entity.*; // 结算领域实体
import com.payment.domain.repository.*; // 结算领域仓储
import com.payment.domain.service.ShardRouteService;
import com.payment.control.service.AlertService; // 告警服务
import com.payment.settlement.account.AccountOperator; // 账户操作组件
import com.payment.settlement.channel.MockPaymentChannel; // 模拟支付渠道
import org.slf4j.Logger; // 日志接口
import org.slf4j.LoggerFactory; // 日志工厂
import org.springframework.stereotype.Service; // Spring 服务注解
import org.springframework.transaction.annotation.Transactional; // 事务注解

import java.math.BigDecimal; // 高精度数值
import java.time.LocalDate; // 本地日期
import java.time.LocalDateTime; // 本地日期时间
import java.util.List; // 列表

/**
 * 结算账户服务实现，提供入账、提现、T+1 批量结算和支付回调处理。
 */
@Service // 注册为 Spring 服务
public class SettleAccountServiceImpl implements SettleAccountService {

    private static final Logger log = LoggerFactory.getLogger(SettleAccountServiceImpl.class); // 日志记录器

    private final AccountOperator accountOperator; // 账户操作组件
    private final MerchantSettleAccountRepository accountRepository; // 商户结算账户仓储
    private final MerchantContractRepository contractRepository; // 商户合约仓储
    private final SettlementOrderEntityRepository settlementOrderRepository; // 结算订单仓储
    private final WithdrawApplyRepository withdrawApplyRepository; // 提现申请仓储
    private final MerchantPayableSuspendRepository suspendRepository; // 应付挂账仓储
    private final MockPaymentChannel paymentChannel; // 模拟支付渠道
    private final AlertService alertService; // 告警服务
    private final ShardRouteService shardRouteService; // 分片路由

    /**
     * 构造注入依赖。
     */
    public SettleAccountServiceImpl(AccountOperator accountOperator,
                                    MerchantSettleAccountRepository accountRepository,
                                    MerchantContractRepository contractRepository,
                                    SettlementOrderEntityRepository settlementOrderRepository,
                                    WithdrawApplyRepository withdrawApplyRepository,
                                    MerchantPayableSuspendRepository suspendRepository,
                                    MockPaymentChannel paymentChannel,
                                    AlertService alertService,
                                    ShardRouteService shardRouteService) {
        this.accountOperator = accountOperator; // 赋值账户操作
        this.accountRepository = accountRepository; // 赋值账户仓储
        this.contractRepository = contractRepository; // 赋值合约仓储
        this.settlementOrderRepository = settlementOrderRepository; // 赋值结算订单仓储
        this.withdrawApplyRepository = withdrawApplyRepository; // 赋值提现仓储
        this.suspendRepository = suspendRepository; // 赋值挂账仓储
        this.paymentChannel = paymentChannel; // 赋值支付渠道
        this.alertService = alertService; // 赋值告警服务
        this.shardRouteService = shardRouteService; // 分片路由
    }

    /**
     * 商户入账，优先冲抵挂账。
     */
    @Override // 实现接口方法
    @Transactional // 开启事务
    public void creditBalance(Long merchantId, String billNo, BigDecimal amount) {
        BigDecimal remain = amount; // 剩余待入账金额
        List<MerchantPayableSuspendEntity> suspends = suspendRepository // 查询未结清挂账
                .findByMerchantIdAndStatusOrderByCreateTimeAsc(merchantId, 0);
        for (MerchantPayableSuspendEntity suspend : suspends) { // 逐笔冲抵
            BigDecimal open = suspend.suspendAmount.subtract(suspend.settledAmount); // 未结清金额
            if (open.signum() <= 0) { // 已结清
                continue; // 跳过
            }
            BigDecimal deduct = remain.min(open); // 本次冲抵金额
            suspend.settledAmount = suspend.settledAmount.add(deduct); // 更新已结清金额
            if (suspend.settledAmount.compareTo(suspend.suspendAmount) >= 0) { // 全部结清
                suspend.status = 1; // 标记已结清
            }
            suspendRepository.save(suspend); // 保存挂账
            remain = remain.subtract(deduct); // 扣减剩余金额
            if (remain.signum() == 0) { // 全部用于冲抵
                return; // 结束
            }
        }
        if (remain.signum() > 0) { // 仍有剩余
            accountOperator.credit(merchantId, billNo, remain, AccountFlowOpType.CREDIT); // 入账到待结算余额
        }
    }

    /**
     * 退款扣减余额，余额不足时创建挂账。
     */
    @Override // 实现接口方法
    @Transactional // 开启事务
    public void debitRefundBalance(Long merchantId, String billNo, BigDecimal amount) {
        try { // 尝试扣款
            accountOperator.debit(merchantId, billNo, amount); // 扣减待结算余额
        } catch (BizException e) { // 业务异常
            if (e.getCode() == ErrorCode.INSUFFICIENT_BALANCE.getCode()) { // 余额不足
                MerchantPayableSuspendEntity suspend = new MerchantPayableSuspendEntity(); // 创建挂账
                suspend.merchantId = merchantId; // 商户 ID
                suspend.billNo = billNo; // 账单号
                suspend.suspendAmount = amount; // 挂账金额
                suspend.settledAmount = BigDecimal.ZERO; // 已结清金额归零
                suspend.status = 0; // 未结清状态
                suspend.createTime = LocalDateTime.now(); // 创建时间
                suspendRepository.save(suspend); // 保存挂账
                return; // 结束
            }
            throw e; // 其他异常继续抛出
        }
    }

    /**
     * 查询商户结算账户余额信息。
     */
    @Override // 实现接口方法
    public SettleAccountDTO queryBalance(Long merchantId) {
        MerchantSettleAccountEntity account = accountRepository.findByMerchantId(merchantId) // 查询账户
                .orElseThrow(() -> BizException.of(ErrorCode.MERCHANT_INVALID)); // 商户不存在
        BigDecimal minWithdraw = contractRepository.findByMerchantId(merchantId) // 查询最低提现额
                .map(c -> c.minWithdraw) // 取合约配置
                .orElse(new BigDecimal("100.00")); // 默认 100 元
        SettleAccountDTO dto = new SettleAccountDTO(); // 创建 DTO
        dto.merchantId = merchantId; // 商户 ID
        dto.waitBalance = account.waitBalance; // 待结算余额
        dto.frozenBalance = account.frozenBalance; // 冻结余额
        dto.availableBalance = account.waitBalance; // 可用余额
        dto.settleMode = account.settleMode; // 结算模式
        dto.minWithdraw = minWithdraw; // 最低提现额
        return dto; // 返回 DTO
    }

    /**
     * 申请 D0 提现。
     */
    @Override // 实现接口方法
    @Transactional // 开启事务
    public WithdrawResultDTO applyWithdraw(WithdrawApplyDTO request) {
        Long merchantId = request.merchantId; // 商户 ID
        BigDecimal amount = request.withdrawAmount; // 提现金额

        MerchantContractEntity contract = contractRepository.findByMerchantId(merchantId) // 查询合约
                .orElseThrow(() -> BizException.of(ErrorCode.MERCHANT_INVALID)); // 商户不存在
        if (contract.settleMode != SettleMode.D0.getCode() && contract.settleMode != SettleMode.S0.getCode()) { // 非 D0/S0 模式
            throw BizException.of(ErrorCode.INVALID_PARAM, "D0 not supported");
        }
        if (amount.compareTo(contract.minWithdraw) < 0) { // 低于最低提现额
            throw BizException.of(ErrorCode.BELOW_MIN_WITHDRAW);
        }

        List<SettlementOrderEntity> paying = settlementOrderRepository.findByMerchantIdAndStatus(merchantId, // 查询支付中订单
                SettleOrderStatus.PAYING.getCode());
        if (!paying.isEmpty()) { // 有支付中订单
            throw BizException.of(ErrorCode.PAYMENT_IN_PROGRESS);
        }

        MerchantSettleAccountEntity account = accountRepository.findByMerchantId(merchantId) // 查询账户
                .orElseThrow(() -> BizException.of(ErrorCode.MERCHANT_INVALID)); // 商户不存在
        if (account.waitBalance.compareTo(amount) < 0) { // 余额不足
            throw BizException.of(ErrorCode.INSUFFICIENT_BALANCE);
        }

        String cardNo = request.settleCardNo != null ? request.settleCardNo : account.settleCardNo; // 结算卡号
        String applyNo = SeqGenerator.applyNo(); // 生成申请单号
        String settleNo = SeqGenerator.settleNo(); // 生成结算单号

        accountOperator.freeze(merchantId, settleNo, amount); // 冻结提现金额

        SettlementOrderEntity order = new SettlementOrderEntity(); // 创建结算订单
        order.settleNo = settleNo; // 结算单号
        order.merchantId = merchantId; // 商户 ID
        order.settleAmount = amount; // 结算金额
        order.settleMode = SettleMode.D0.getCode(); // D0 模式
        order.settleCardNo = cardNo; // 结算卡号
        order.status = SettleOrderStatus.PAYING.getCode(); // 支付中状态
        order.createTime = LocalDateTime.now(); // 创建时间
        order.updateTime = LocalDateTime.now(); // 更新时间
        settlementOrderRepository.save(order); // 保存订单
        shardRouteService.registerSettleRoute(settleNo, merchantId); // 注册分片路由

        WithdrawApplyEntity apply = new WithdrawApplyEntity(); // 创建提现申请
        apply.applyNo = applyNo; // 申请单号
        apply.merchantId = merchantId; // 商户 ID
        apply.amount = amount; // 提现金额
        apply.settleNo = settleNo; // 关联结算单号
        apply.status = 1; // 处理中状态
        apply.createTime = LocalDateTime.now(); // 创建时间
        withdrawApplyRepository.save(apply); // 保存申请

        paymentChannel.submitAsync(settleNo, amount); // 异步提交支付

        account = accountRepository.findByMerchantId(merchantId).orElseThrow(); // 重新查询账户
        WithdrawResultDTO result = new WithdrawResultDTO(); // 创建结果 DTO
        result.applyNo = applyNo; // 申请单号
        result.settleNo = settleNo; // 结算单号
        result.status = 1; // 处理中状态
        result.waitBalance = account.waitBalance; // 待结算余额
        result.frozenBalance = account.frozenBalance; // 冻结余额
        return result; // 返回结果
    }

    /**
     * 处理支付渠道回调。
     */
    @Override // 实现接口方法
    @Transactional // 开启事务
    public void handlePaymentCallback(PaymentCallbackDTO callback) {
        SettlementOrderEntity order = settlementOrderRepository.findBySettleNo(callback.settleNo) // 查询结算订单
                .orElseThrow(() -> BizException.of(ErrorCode.INVALID_PARAM, "settle not found")); // 订单不存在

        if (order.status == SettleOrderStatus.SUCCESS.getCode()) { // 已成功
            return; // 幂等返回
        }

        if ("SUCCESS".equalsIgnoreCase(callback.status)) { // 支付成功
            accountOperator.deductFrozen(order.merchantId, order.settleNo, order.settleAmount); // 扣减冻结余额
            order.status = SettleOrderStatus.SUCCESS.getCode(); // 更新为成功
            order.channelTradeNo = callback.channelTradeNo; // 记录渠道流水号
        } else { // 支付失败
            accountOperator.unfreeze(order.merchantId, order.settleNo, order.settleAmount); // 解冻余额
            order.status = SettleOrderStatus.FAILED.getCode(); // 更新为失败
            order.failReason = callback.failReason; // 记录失败原因
            alertService.send(AlertService.PAYMENT_FAIL, order.settleNo + ": " + callback.failReason); // 发送告警
        }
        order.updateTime = LocalDateTime.now(); // 更新时间
        settlementOrderRepository.save(order); // 保存订单

        withdrawApplyRepository.findAll().stream() // 更新关联提现申请
                .filter(w -> order.settleNo.equals(w.settleNo)) // 匹配结算单号
                .findFirst() // 取第一条
                .ifPresent(w -> { // 存在则更新
                    w.status = "SUCCESS".equalsIgnoreCase(callback.status) ? 2 : 3; // 2 成功 3 失败
                    withdrawApplyRepository.save(w); // 保存申请
                });
    }

    /**
     * 执行 T+1 批量结算。
     */
    @Override // 实现接口方法
    public void runT1Batch(LocalDate batchDate) {
        String batchNo = "BATCH-T1-" + batchDate; // 批次号
        if (settlementOrderRepository.existsByOriginSettleNo(batchNo)) { // 批次已执行
            log.info("T1 batch already ran batchNo={}", batchNo); // 记录日志
            return; // 跳过
        }

        List<MerchantSettleAccountEntity> accounts = accountRepository // 查询 T1 模式且余额≥1 的账户
                .findBySettleModeAndWaitBalanceGreaterThanEqualOrderByMerchantIdAsc(
                        SettleMode.T1.getCode(), BigDecimal.ONE);

        int processed = 0; // 处理计数
        for (MerchantSettleAccountEntity account : accounts) { // 逐个处理
            try { // 处理单个商户
                if (processOneT1(account, batchNo)) { // 处理成功
                    processed++; // 计数加一
                }
            } catch (Exception e) { // 处理失败
                log.warn("T1 batch failed merchantId={} batchNo={}", account.merchantId, batchNo, e); // 记录警告
            }
        }
        log.info("T1 batch finished batchNo={} processed={}", batchNo, processed); // 记录完成日志
    }

    /**
     * 处理单个商户的 T+1 结算。
     */
    private boolean processOneT1(MerchantSettleAccountEntity account, String batchNo) {
        BigDecimal minSettle = contractRepository.findByMerchantId(account.merchantId) // 查询最低结算额
                .map(c -> c.minWithdraw) // 取合约配置
                .orElse(new BigDecimal("1.00")); // 默认 1 元
        if (account.waitBalance.compareTo(minSettle) < 0) { // 余额不足
            return false; // 跳过
        }
        if (account.settleCardNo == null || account.settleCardNo.isBlank()) { // 无结算卡
            return false; // 跳过
        }

        List<SettlementOrderEntity> paying = settlementOrderRepository.findByMerchantIdAndStatus( // 查询支付中订单
                account.merchantId, SettleOrderStatus.PAYING.getCode());
        if (!paying.isEmpty()) { // 有支付中订单
            return false; // 跳过
        }

        BigDecimal amount = account.waitBalance; // 全额结算
        String settleNo = SeqGenerator.settleNo(); // 生成结算单号
        accountOperator.freeze(account.merchantId, settleNo, amount); // 冻结余额

        SettlementOrderEntity order = new SettlementOrderEntity(); // 创建结算订单
        order.settleNo = settleNo; // 结算单号
        order.merchantId = account.merchantId; // 商户 ID
        order.settleAmount = amount; // 结算金额
        order.settleMode = SettleMode.T1.getCode(); // T1 模式
        order.settleCardNo = account.settleCardNo; // 结算卡号
        order.status = SettleOrderStatus.PAYING.getCode(); // 支付中状态
        order.originSettleNo = batchNo; // 关联批次号
        order.createTime = LocalDateTime.now(); // 创建时间
        order.updateTime = LocalDateTime.now(); // 更新时间
        settlementOrderRepository.save(order); // 保存订单
        shardRouteService.registerSettleRoute(settleNo, account.merchantId); // 注册分片路由

        paymentChannel.submitAsync(settleNo, amount); // 异步提交支付
        return true; // 处理成功
    }

    /**
     * 重试失败的支付订单。
     */
    @Override // 实现接口方法
    @Transactional // 开启事务
    public void retryFailedPayments(int limit) {
        List<SettlementOrderEntity> failed = settlementOrderRepository.findByStatus(SettleOrderStatus.FAILED.getCode()); // 查询失败订单
        int retried = 0; // 重试计数
        for (SettlementOrderEntity order : failed) { // 逐个处理
            if (retried >= limit) { // 达到上限
                break; // 停止
            }
            if (settlementOrderRepository.existsByOriginSettleNoAndStatusNot(order.settleNo, SettleOrderStatus.FAILED.getCode())) { // 已有重试订单
                continue; // 跳过
            }
            try { // 执行重试
                retryOnePayment(order); // 重试单笔
                retried++; // 计数加一
            } catch (Exception e) { // 重试失败
                log.warn("payment retry failed settleNo={}", order.settleNo, e); // 记录警告
            }
        }
        if (retried > 0) { // 有重试成功
            log.info("payment retry finished count={}", retried); // 记录完成日志
        }
    }

    /**
     * 重试单笔失败支付。
     */
    private void retryOnePayment(SettlementOrderEntity failedOrder) {
        MerchantSettleAccountEntity account = accountRepository.findByMerchantId(failedOrder.merchantId) // 查询账户
                .orElseThrow(() -> BizException.of(ErrorCode.MERCHANT_INVALID)); // 商户不存在
        if (account.waitBalance.compareTo(failedOrder.settleAmount) < 0) { // 余额不足
            return; // 跳过
        }

        String settleNo = SeqGenerator.settleNo(); // 生成新结算单号
        accountOperator.freeze(failedOrder.merchantId, settleNo, failedOrder.settleAmount); // 冻结金额

        SettlementOrderEntity order = new SettlementOrderEntity(); // 创建新结算订单
        order.settleNo = settleNo; // 结算单号
        order.merchantId = failedOrder.merchantId; // 商户 ID
        order.settleAmount = failedOrder.settleAmount; // 结算金额
        order.settleMode = failedOrder.settleMode; // 结算模式
        order.settleCardNo = failedOrder.settleCardNo; // 结算卡号
        order.status = SettleOrderStatus.PAYING.getCode(); // 支付中状态
        order.originSettleNo = failedOrder.settleNo; // 关联原失败单号
        order.createTime = LocalDateTime.now(); // 创建时间
        order.updateTime = LocalDateTime.now(); // 更新时间
        settlementOrderRepository.save(order); // 保存订单
        shardRouteService.registerSettleRoute(settleNo, failedOrder.merchantId); // 注册分片路由

        paymentChannel.submitAsync(settleNo, failedOrder.settleAmount); // 异步提交支付
    }
}
