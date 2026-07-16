package com.payment.settlement.support;

import com.payment.api.dto.PaymentCallbackDTO;
import com.payment.common.enums.AccountFlowOpType;
import com.payment.common.enums.SettleOrderStatus;
import com.payment.domain.entity.MerchantPayableSuspendEntity;
import com.payment.domain.entity.SettlementOrderEntity;
import com.payment.domain.repository.AccountFlowRepository;
import com.payment.domain.repository.MerchantPayableSuspendRepository;
import com.payment.domain.repository.SettlementOrderEntityRepository;
import com.payment.domain.repository.WithdrawApplyRepository;
import com.payment.settlement.account.AccountOperator;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 结算短事务：账户变动与订单更新同事务，告警/MQ 在事务外执行。
 */
@Component
public class SettleAccountTxSupport {

    private final AccountOperator accountOperator;
    private final AccountFlowRepository accountFlowRepository;
    private final MerchantPayableSuspendRepository suspendRepository;
    private final SettlementOrderEntityRepository settlementOrderRepository;
    private final WithdrawApplyRepository withdrawApplyRepository;

    public SettleAccountTxSupport(AccountOperator accountOperator,
                                  AccountFlowRepository accountFlowRepository,
                                  MerchantPayableSuspendRepository suspendRepository,
                                  SettlementOrderEntityRepository settlementOrderRepository,
                                  WithdrawApplyRepository withdrawApplyRepository) {
        this.accountOperator = accountOperator;
        this.accountFlowRepository = accountFlowRepository;
        this.suspendRepository = suspendRepository;
        this.settlementOrderRepository = settlementOrderRepository;
        this.withdrawApplyRepository = withdrawApplyRepository;
    }

    /**
     * 入账：挂账冲抵 + 余额贷记（幂等由流水 uk 保证）。
     */
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

    /**
     * 支付回调：账户操作 + 结算单/提现申请单 SQL 更新。
     *
     * @return true 表示支付成功
     */
    @Transactional
    public boolean applyPaymentCallback(SettlementOrderEntity order, PaymentCallbackDTO callback) {
        boolean success = "SUCCESS".equalsIgnoreCase(callback.status);
        if (success) {
            accountOperator.deductFrozen(order.merchantId, order.settleNo, order.settleAmount);
        } else {
            accountOperator.unfreeze(order.merchantId, order.settleNo, order.settleAmount);
        }
        int newStatus = success ? SettleOrderStatus.SUCCESS.getCode() : SettleOrderStatus.FAILED.getCode();
        int updated = settlementOrderRepository.updatePaymentResult(
                order.settleNo, order.merchantId, SettleOrderStatus.PAYING.getCode(), newStatus,
                callback.channelTradeNo, callback.failReason, LocalDateTime.now());
        if (updated == 0) {
            return success;
        }
        int withdrawStatus = success ? 2 : 3;
        withdrawApplyRepository.updateStatusBySettleNoAndMerchantId(
                order.settleNo, order.merchantId, withdrawStatus);
        return success;
    }
}
