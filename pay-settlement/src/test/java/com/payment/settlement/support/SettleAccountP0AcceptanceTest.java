package com.payment.settlement.support;

import com.payment.api.dto.PaymentCallbackDTO;
import com.payment.api.dto.WithdrawApplyDTO;
import com.payment.common.enums.SettleOrderStatus;
import com.payment.domain.entity.MerchantSettleAccountEntity;
import com.payment.domain.entity.SettlementOrderEntity;
import com.payment.domain.repository.AccountFlowRepository;
import com.payment.domain.repository.MerchantPayableSuspendRepository;
import com.payment.domain.repository.MerchantSettleAccountRepository;
import com.payment.domain.repository.SettlementOrderEntityRepository;
import com.payment.domain.repository.WithdrawApplyRepository;
import com.payment.domain.service.ShardRouteService;
import com.payment.settlement.account.AccountOperator;
import com.payment.settlement.channel.MockPaymentChannel;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.math.BigDecimal;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * P0-1 验收：先 CAS 后资金；渠道仅事务提交后调用。
 */
@ExtendWith(MockitoExtension.class)
class SettleAccountP0AcceptanceTest {

    @Mock AccountOperator accountOperator;
    @Mock AccountFlowRepository accountFlowRepository;
    @Mock MerchantPayableSuspendRepository suspendRepository;
    @Mock MerchantSettleAccountRepository accountRepository;
    @Mock SettlementOrderEntityRepository settlementOrderRepository;
    @Mock WithdrawApplyRepository withdrawApplyRepository;
    @Mock ShardRouteService shardRouteService;
    @Mock MockPaymentChannel paymentChannel;
    @InjectMocks SettleAccountTxSupport txSupport;

    @AfterEach
    void clearTxSync() {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.clearSynchronization();
        }
        if (TransactionSynchronizationManager.isActualTransactionActive()) {
            TransactionSynchronizationManager.setActualTransactionActive(false);
        }
    }

    @Test
    void dualCallback_onlyFirstCasWinsFunds() {
        SettlementOrderEntity order = payingOrder();
        PaymentCallbackDTO cb = successCallback();

        when(settlementOrderRepository.updatePaymentResult(
                any(), any(), any(), any(), any(), any(), any()))
                .thenReturn(1)
                .thenReturn(0);

        assertTrue(txSupport.applyPaymentCallback(order, cb));
        assertTrue(txSupport.applyPaymentCallback(order, cb));

        verify(accountOperator, times(1)).deductFrozen(eq(10001L), eq("S1"), eq(new BigDecimal("10.00")));
        verify(accountOperator, never()).unfreeze(any(), any(), any());
        verify(withdrawApplyRepository, times(1))
                .updateStatusBySettleNoAndMerchantId("S1", 10001L, 2);
    }

    @Test
    void withdraw_channelNotCalledUntilAfterCommit_andSkippedOnRollback() {
        WithdrawApplyDTO request = new WithdrawApplyDTO();
        request.merchantId = 10001L;
        request.withdrawAmount = new BigDecimal("50.00");

        MerchantSettleAccountEntity account = new MerchantSettleAccountEntity();
        account.waitBalance = new BigDecimal("100.00");
        account.frozenBalance = new BigDecimal("50.00");
        when(accountRepository.findByMerchantId(10001L)).thenReturn(java.util.Optional.of(account));

        TransactionSynchronizationManager.initSynchronization();
        TransactionSynchronizationManager.setActualTransactionActive(true);
        try {
            txSupport.applyWithdrawLocal(request, "WD1", "S-WD1", "6222");

            verify(paymentChannel, never()).submitAsync(any(), any());

            List<TransactionSynchronization> syncs =
                    TransactionSynchronizationManager.getSynchronizations();
            for (TransactionSynchronization sync : syncs) {
                sync.afterCompletion(TransactionSynchronization.STATUS_ROLLED_BACK);
            }
            verify(paymentChannel, never()).submitAsync(any(), any());
        } finally {
            TransactionSynchronizationManager.clearSynchronization();
            TransactionSynchronizationManager.setActualTransactionActive(false);
        }
    }

    @Test
    void withdraw_channelCalledOnlyAfterCommit() {
        WithdrawApplyDTO request = new WithdrawApplyDTO();
        request.merchantId = 10001L;
        request.withdrawAmount = new BigDecimal("50.00");

        MerchantSettleAccountEntity account = new MerchantSettleAccountEntity();
        account.waitBalance = new BigDecimal("100.00");
        account.frozenBalance = new BigDecimal("50.00");
        when(accountRepository.findByMerchantId(10001L)).thenReturn(java.util.Optional.of(account));

        TransactionSynchronizationManager.initSynchronization();
        TransactionSynchronizationManager.setActualTransactionActive(true);
        try {
            txSupport.applyWithdrawLocal(request, "WD2", "S-WD2", "6222");
            verify(paymentChannel, never()).submitAsync(any(), any());

            for (TransactionSynchronization sync : TransactionSynchronizationManager.getSynchronizations()) {
                sync.afterCommit();
            }
            verify(paymentChannel, times(1)).submitAsync("S-WD2", new BigDecimal("50.00"));
        } finally {
            TransactionSynchronizationManager.clearSynchronization();
            TransactionSynchronizationManager.setActualTransactionActive(false);
        }
    }

    private static SettlementOrderEntity payingOrder() {
        SettlementOrderEntity order = new SettlementOrderEntity();
        order.settleNo = "S1";
        order.merchantId = 10001L;
        order.settleAmount = new BigDecimal("10.00");
        order.status = SettleOrderStatus.PAYING.getCode();
        return order;
    }

    private static PaymentCallbackDTO successCallback() {
        PaymentCallbackDTO cb = new PaymentCallbackDTO();
        cb.status = "SUCCESS";
        cb.channelTradeNo = "BK1";
        return cb;
    }
}
