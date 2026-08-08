package com.payment.calc.service;

import com.payment.api.service.FeeCalcService;
import com.payment.api.service.SplitService;
import com.payment.common.enums.BillStatus;
import com.payment.common.enums.TaskStatus;
import com.payment.domain.entity.TradeBillEntity;
import com.payment.domain.repository.ClearanceTaskRepository;
import com.payment.domain.repository.TradeBillRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * C1 验收：finalize 仅用 claim 上下文的 merchantId CAS，不走 findByBillNo。
 */
@ExtendWith(MockitoExtension.class)
class ClearanceFinalizeC1Test {

    @Mock ClearanceTaskRepository clearanceTaskRepository;
    @Mock TradeBillRepository tradeBillRepository;
    @Mock FeeCalcService feeCalcService;
    @Mock SplitService splitService;
    @InjectMocks ClearanceTaskTxSupport txSupport;

    @Test
    void finalizeSuccess_usesMerchantIdCas_withoutFindByBillNo() {
        ClearanceClaimContext ctx = ctx("B1", 10001L);
        when(tradeBillRepository.updateStatusByBillNoAndMerchantId(
                "B1", 10001L, BillStatus.CLEARING.getCode(), BillStatus.CLEARED.getCode()))
                .thenReturn(1);
        when(clearanceTaskRepository.markSuccess(
                eq("B1"), eq(10001L), eq(TaskStatus.RUNNING.getCode()),
                eq(TaskStatus.SUCCESS.getCode()), any(LocalDateTime.class)))
                .thenReturn(1);

        assertDoesNotThrow(() -> txSupport.finalizeSuccess(ctx));

        verify(clearanceTaskRepository, never()).findByBillNo(any());
        verify(tradeBillRepository, never()).findByBillNo(any());
        verify(tradeBillRepository).updateStatusByBillNoAndMerchantId(
                "B1", 10001L, BillStatus.CLEARING.getCode(), BillStatus.CLEARED.getCode());
        verify(clearanceTaskRepository).markSuccess(
                eq("B1"), eq(10001L), eq(TaskStatus.RUNNING.getCode()),
                eq(TaskStatus.SUCCESS.getCode()), any(LocalDateTime.class));
    }

    @Test
    void finalizeSuccess_throwsStatusMismatch_whenBillCasMisses() {
        ClearanceClaimContext ctx = ctx("B2", 10001L);
        when(tradeBillRepository.updateStatusByBillNoAndMerchantId(
                "B2", 10001L, BillStatus.CLEARING.getCode(), BillStatus.CLEARED.getCode()))
                .thenReturn(0);

        IllegalStateException ex = assertThrows(IllegalStateException.class,
                () -> txSupport.finalizeSuccess(ctx));
        assertTrue(ex.getMessage().contains("finalize bill status mismatch"));
        verify(clearanceTaskRepository, never()).markSuccess(any(), any(), any(), any(), any());
        verify(clearanceTaskRepository, never()).findByBillNo(any());
    }

    @Test
    void finalizeSuccess_throwsStatusMismatch_whenTaskCasMisses() {
        ClearanceClaimContext ctx = ctx("B3", 10001L);
        when(tradeBillRepository.updateStatusByBillNoAndMerchantId(
                "B3", 10001L, BillStatus.CLEARING.getCode(), BillStatus.CLEARED.getCode()))
                .thenReturn(1);
        when(clearanceTaskRepository.markSuccess(
                eq("B3"), eq(10001L), eq(TaskStatus.RUNNING.getCode()),
                eq(TaskStatus.SUCCESS.getCode()), any(LocalDateTime.class)))
                .thenReturn(0);

        IllegalStateException ex = assertThrows(IllegalStateException.class,
                () -> txSupport.finalizeSuccess(ctx));
        assertTrue(ex.getMessage().contains("finalize task status mismatch"));
        verify(clearanceTaskRepository, never()).findByBillNo(any());
    }

    private static ClearanceClaimContext ctx(String billNo, Long merchantId) {
        TradeBillEntity bill = new TradeBillEntity();
        bill.billNo = billNo;
        bill.merchantId = merchantId;
        bill.status = BillStatus.CLEARING.getCode();
        return new ClearanceClaimContext(bill, merchantId);
    }
}
