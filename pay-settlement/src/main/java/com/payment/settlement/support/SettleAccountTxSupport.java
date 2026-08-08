package com.payment.settlement.support;

import com.payment.api.dto.PaymentCallbackDTO;
import com.payment.api.dto.WithdrawApplyDTO;
import com.payment.api.dto.WithdrawResultDTO;
import com.payment.common.enums.AccountFlowOpType;
import com.payment.common.enums.SettleMode;
import com.payment.common.enums.SettleOrderStatus;
import com.payment.common.exception.BizException;
import com.payment.common.exception.ErrorCode;
import com.payment.common.tx.AfterCommit;
import com.payment.domain.entity.MerchantPayableSuspendEntity;
import com.payment.domain.entity.MerchantSettleAccountEntity;
import com.payment.domain.entity.SettlementOrderEntity;
import com.payment.domain.entity.WithdrawApplyEntity;
import com.payment.domain.repository.AccountFlowRepository;
import com.payment.domain.repository.MerchantPayableSuspendRepository;
import com.payment.domain.repository.MerchantSettleAccountRepository;
import com.payment.domain.repository.SettlementOrderEntityRepository;
import com.payment.domain.repository.WithdrawApplyRepository;
import com.payment.domain.service.ShardRouteService;
import com.payment.settlement.account.AccountOperator;
import com.payment.settlement.channel.MockPaymentChannel;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 结算短事务唯一边界：先 CAS 订单再动资金；渠道走 AfterCommit。
 */
@Component
public class SettleAccountTxSupport {

    private final AccountOperator accountOperator;
    private final AccountFlowRepository accountFlowRepository;
    private final MerchantPayableSuspendRepository suspendRepository;
    private final MerchantSettleAccountRepository accountRepository;
    private final SettlementOrderEntityRepository settlementOrderRepository;
    private final WithdrawApplyRepository withdrawApplyRepository;
    private final ShardRouteService shardRouteService;
    private final MockPaymentChannel paymentChannel;

    public SettleAccountTxSupport(AccountOperator accountOperator,
                                  AccountFlowRepository accountFlowRepository,
                                  MerchantPayableSuspendRepository suspendRepository,
                                  MerchantSettleAccountRepository accountRepository,
                                  SettlementOrderEntityRepository settlementOrderRepository,
                                  WithdrawApplyRepository withdrawApplyRepository,
                                  ShardRouteService shardRouteService,
                                  MockPaymentChannel paymentChannel) {
        this.accountOperator = accountOperator;
        this.accountFlowRepository = accountFlowRepository;
        this.suspendRepository = suspendRepository;
        this.accountRepository = accountRepository;
        this.settlementOrderRepository = settlementOrderRepository;
        this.withdrawApplyRepository = withdrawApplyRepository;
        this.shardRouteService = shardRouteService;
        this.paymentChannel = paymentChannel;
    }

    @Transactional
    public void creditBalance(Long merchantId, String billNo, BigDecimal amount) {
        if (accountFlowRepository.existsByBillNoAndOpType(billNo, AccountFlowOpType.CREDIT.getCode())) {
            return;
        }
        BigDecimal remain = amount;
        List<MerchantPayableSuspendEntity> suspends = suspendRepository
                .findByMerchantIdAndStatusOrderByCreateTimeAsc(merchantId, 0);
        for (MerchantPayableSuspendEntity suspend : suspends) {
            BigDecimal open = suspend.suspendAmount.subtract(suspend.settledAmount);
            if (open.signum() <= 0) {
                continue;
            }
            BigDecimal deduct = remain.min(open);
            if (!suspendRepository.applySettlementOffset(suspend.id, merchantId, deduct)) {
                continue;
            }
            remain = remain.subtract(deduct);
            if (remain.signum() == 0) {
                return;
            }
        }
        if (remain.signum() > 0) {
            accountOperator.credit(merchantId, billNo, remain, AccountFlowOpType.CREDIT);
        }
    }

    @Transactional
    public void debitRefundBalance(Long merchantId, String billNo, BigDecimal amount) {
        try {
            accountOperator.debit(merchantId, billNo, amount);
        } catch (BizException e) {
            if (e.getCode() == ErrorCode.INSUFFICIENT_BALANCE.getCode()) {
                MerchantPayableSuspendEntity suspend = new MerchantPayableSuspendEntity();
                suspend.merchantId = merchantId;
                suspend.billNo = billNo;
                suspend.suspendAmount = amount;
                suspend.settledAmount = BigDecimal.ZERO;
                suspend.status = 0;
                suspend.createTime = LocalDateTime.now();
                suspendRepository.save(suspend);
                return;
            }
            throw e;
        }
    }

    @Transactional
    public WithdrawResultDTO applyWithdrawLocal(WithdrawApplyDTO request,
                                                String applyNo,
                                                String settleNo,
                                                String cardNo) {
        Long merchantId = request.merchantId;
        BigDecimal amount = request.withdrawAmount;
        accountOperator.freeze(merchantId, settleNo, amount);

        SettlementOrderEntity order = new SettlementOrderEntity();
        order.settleNo = settleNo;
        order.merchantId = merchantId;
        order.settleAmount = amount;
        order.settleMode = SettleMode.D0.getCode();
        order.settleCardNo = cardNo;
        order.status = SettleOrderStatus.PAYING.getCode();
        order.createTime = LocalDateTime.now();
        order.updateTime = LocalDateTime.now();
        settlementOrderRepository.save(order);
        shardRouteService.registerSettleRoute(settleNo, merchantId);

        WithdrawApplyEntity apply = new WithdrawApplyEntity();
        apply.applyNo = applyNo;
        apply.merchantId = merchantId;
        apply.amount = amount;
        apply.settleNo = settleNo;
        apply.status = 1;
        apply.createTime = LocalDateTime.now();
        withdrawApplyRepository.save(apply);

        AfterCommit.run(() -> paymentChannel.submitAsync(settleNo, amount));

        MerchantSettleAccountEntity updated = accountRepository.findByMerchantId(merchantId).orElseThrow();
        WithdrawResultDTO result = new WithdrawResultDTO();
        result.applyNo = applyNo;
        result.settleNo = settleNo;
        result.status = 1;
        result.waitBalance = updated.waitBalance;
        result.frozenBalance = updated.frozenBalance;
        return result;
    }

    /**
     * 先 CAS 抢订单状态，成功后再动资金；CAS 失败则不做资金写（幂等安静返回 true）。
     */
    @Transactional
    public boolean applyPaymentCallback(SettlementOrderEntity order, PaymentCallbackDTO callback) {
        boolean success = "SUCCESS".equalsIgnoreCase(callback.status);
        int newStatus = success ? SettleOrderStatus.SUCCESS.getCode() : SettleOrderStatus.FAILED.getCode();
        int updated = settlementOrderRepository.updatePaymentResult(
                order.settleNo, order.merchantId, SettleOrderStatus.PAYING.getCode(), newStatus,
                callback.channelTradeNo, callback.failReason, LocalDateTime.now());
        if (updated == 0) {
            return true;
        }
        if (success) {
            accountOperator.deductFrozen(order.merchantId, order.settleNo, order.settleAmount);
        } else {
            accountOperator.unfreeze(order.merchantId, order.settleNo, order.settleAmount);
        }
        withdrawApplyRepository.updateStatusBySettleNoAndMerchantId(
                order.settleNo, order.merchantId, success ? 2 : 3);
        return success;
    }

    @Transactional
    public void processOneT1Local(MerchantSettleAccountEntity account, String batchNo,
                                  String settleNo, BigDecimal amount) {
        accountOperator.freeze(account.merchantId, settleNo, amount);
        SettlementOrderEntity order = new SettlementOrderEntity();
        order.settleNo = settleNo;
        order.merchantId = account.merchantId;
        order.settleAmount = amount;
        order.settleMode = SettleMode.T1.getCode();
        order.settleCardNo = account.settleCardNo;
        order.status = SettleOrderStatus.PAYING.getCode();
        order.originSettleNo = batchNo;
        order.createTime = LocalDateTime.now();
        order.updateTime = LocalDateTime.now();
        settlementOrderRepository.save(order);
        shardRouteService.registerSettleRoute(settleNo, account.merchantId);
        AfterCommit.run(() -> paymentChannel.submitAsync(settleNo, amount));
    }

    @Transactional
    public void retryOnePaymentLocal(SettlementOrderEntity failedOrder, String settleNo) {
        accountOperator.freeze(failedOrder.merchantId, settleNo, failedOrder.settleAmount);
        SettlementOrderEntity order = new SettlementOrderEntity();
        order.settleNo = settleNo;
        order.merchantId = failedOrder.merchantId;
        order.settleAmount = failedOrder.settleAmount;
        order.settleMode = failedOrder.settleMode;
        order.settleCardNo = failedOrder.settleCardNo;
        order.status = SettleOrderStatus.PAYING.getCode();
        order.originSettleNo = failedOrder.settleNo;
        order.createTime = LocalDateTime.now();
        order.updateTime = LocalDateTime.now();
        settlementOrderRepository.save(order);
        shardRouteService.registerSettleRoute(settleNo, failedOrder.merchantId);
        AfterCommit.run(() -> paymentChannel.submitAsync(settleNo, failedOrder.settleAmount));
    }
}
