package com.payment.settlement.support;

import com.payment.api.dto.PaymentCallbackDTO;
import com.payment.domain.entity.SettlementOrderEntity;
import com.payment.domain.repository.AccountFlowRepository;
import com.payment.domain.repository.MerchantPayableSuspendRepository;
import com.payment.domain.repository.MerchantSettleAccountRepository;
import com.payment.domain.repository.SettlementOrderEntityRepository;
import com.payment.domain.repository.WithdrawApplyRepository;
import com.payment.domain.service.ShardRouteService;
import com.payment.settlement.account.AccountOperator;
import com.payment.settlement.channel.MockPaymentChannel;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SettleAccountTxSupportCallbackTest {

    @Mock AccountOperator accountOperator;
    @Mock AccountFlowRepository accountFlowRepository;
    @Mock MerchantPayableSuspendRepository suspendRepository;
    @Mock MerchantSettleAccountRepository accountRepository;
    @Mock SettlementOrderEntityRepository settlementOrderRepository;
    @Mock WithdrawApplyRepository withdrawApplyRepository;
    @Mock ShardRouteService shardRouteService;
    @Mock MockPaymentChannel paymentChannel;
    @InjectMocks SettleAccountTxSupport txSupport;

    @Test
    void applyPaymentCallback_skipsFunds_whenCasMisses() {
        SettlementOrderEntity order = new SettlementOrderEntity();
        order.settleNo = "S1";
        order.merchantId = 10001L;
        order.settleAmount = new BigDecimal("10.00");
        PaymentCallbackDTO cb = new PaymentCallbackDTO();
        cb.status = "SUCCESS";
        when(settlementOrderRepository.updatePaymentResult(
                any(), any(), any(), any(), any(), any(), any())).thenReturn(0);

        txSupport.applyPaymentCallback(order, cb);

        verify(accountOperator, never()).deductFrozen(any(), any(), any());
        verify(accountOperator, never()).unfreeze(any(), any(), any());
        verify(withdrawApplyRepository, never())
                .updateStatusBySettleNoAndMerchantId(any(), any(), any());
    }
}
