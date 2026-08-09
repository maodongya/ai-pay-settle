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
import org.mockito.InOrder;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * C3/P0-2：Watchdog / fail 不预读整行；markFailed 后轻量查 status；先 task 后 bill。
 */
@ExtendWith(MockitoExtension.class)
class ClearanceWatchdogP0AcceptanceTest {

    @Mock ClearanceTaskRepository clearanceTaskRepository;
    @Mock TradeBillRepository tradeBillRepository;
    @Mock FeeCalcService feeCalcService;
    @Mock SplitService splitService;
    @InjectMocks ClearanceTaskTxSupport txSupport;

    @Test
    void watchdog_updatesTaskThenBill_whenStillClearing() {
        when(clearanceTaskRepository.markFailed(
                eq("B1"), eq(10001L), eq(TaskStatus.RUNNING.getCode()),
                eq(TaskStatus.FAILED.getCode()), eq(TaskStatus.DEAD.getCode()),
                anyInt(), eq("watchdog timeout"), any(LocalDateTime.class)))
                .thenReturn(1);
        when(clearanceTaskRepository.findStatusByBillNoAndMerchantId("B1", 10001L))
                .thenReturn(Optional.of(TaskStatus.FAILED.getCode()));
        when(tradeBillRepository.updateStatusByBillNoAndMerchantId(
                "B1", 10001L, BillStatus.CLEARING.getCode(), BillStatus.FAILED.getCode()))
                .thenReturn(1);

        ClearanceFailureOutcome out = txSupport.watchdogFail("B1", 10001L);

        assertTrue(out.updated());
        assertFalse(out.enteredDead());
        verify(clearanceTaskRepository, never()).findByBillNoAndMerchantId(any(), any());
        InOrder order = inOrder(clearanceTaskRepository, tradeBillRepository);
        order.verify(clearanceTaskRepository).markFailed(
                eq("B1"), eq(10001L), eq(TaskStatus.RUNNING.getCode()),
                eq(TaskStatus.FAILED.getCode()), eq(TaskStatus.DEAD.getCode()),
                anyInt(), eq("watchdog timeout"), any(LocalDateTime.class));
        order.verify(clearanceTaskRepository).findStatusByBillNoAndMerchantId("B1", 10001L);
        order.verify(tradeBillRepository).updateStatusByBillNoAndMerchantId(
                "B1", 10001L, BillStatus.CLEARING.getCode(), BillStatus.FAILED.getCode());
    }

    @Test
    void watchdog_stillUpdated_whenBillCasMisses_butLogsMismatch() {
        when(clearanceTaskRepository.markFailed(any(), any(), any(), any(), any(),
                anyInt(), any(), any())).thenReturn(1);
        when(clearanceTaskRepository.findStatusByBillNoAndMerchantId("B1b", 10001L))
                .thenReturn(Optional.of(TaskStatus.FAILED.getCode()));
        when(tradeBillRepository.updateStatusByBillNoAndMerchantId(
                "B1b", 10001L, BillStatus.CLEARING.getCode(), BillStatus.FAILED.getCode()))
                .thenReturn(0);

        ClearanceFailureOutcome out = txSupport.watchdogFail("B1b", 10001L);

        assertTrue(out.updated());
        verify(tradeBillRepository).updateStatusByBillNoAndMerchantId(
                "B1b", 10001L, BillStatus.CLEARING.getCode(), BillStatus.FAILED.getCode());
    }

    @Test
    void watchdog_doesNotTouchBill_whenTaskAlreadyLeftRunning() {
        when(clearanceTaskRepository.markFailed(any(), any(), any(), any(), any(),
                anyInt(), any(), any())).thenReturn(0);

        ClearanceFailureOutcome out = txSupport.watchdogFail("B2", 10001L);

        assertFalse(out.updated());
        verify(tradeBillRepository, never()).updateStatusByBillNoAndMerchantId(any(), any(), any(), any());
        verify(clearanceTaskRepository, never()).findStatusByBillNoAndMerchantId(any(), any());
        verify(clearanceTaskRepository, never()).findByBillNoAndMerchantId(any(), any());
    }

    @Test
    void watchdog_enteredDead_whenStatusBecomesDead() {
        when(clearanceTaskRepository.markFailed(any(), any(), any(), any(), any(),
                anyInt(), any(), any())).thenReturn(1);
        when(clearanceTaskRepository.findStatusByBillNoAndMerchantId("B3", 10001L))
                .thenReturn(Optional.of(TaskStatus.DEAD.getCode()));
        when(tradeBillRepository.updateStatusByBillNoAndMerchantId(any(), any(), any(), any()))
                .thenReturn(1);

        ClearanceFailureOutcome out = txSupport.watchdogFail("B3", 10001L);

        assertTrue(out.updated());
        assertTrue(out.enteredDead());
        verify(clearanceTaskRepository, never()).findByBillNoAndMerchantId(any(), any());
    }

    @Test
    void markFailure_doesNotFindEntityBeforeMarkFailed() {
        TradeBillEntity bill = new TradeBillEntity();
        bill.billNo = "B4";
        bill.merchantId = 10001L;
        ClearanceClaimContext ctx = new ClearanceClaimContext(bill, 10001L);
        when(clearanceTaskRepository.markFailed(any(), any(), any(), any(), any(),
                anyInt(), any(), any())).thenReturn(1);
        when(clearanceTaskRepository.findStatusByBillNoAndMerchantId("B4", 10001L))
                .thenReturn(Optional.of(TaskStatus.FAILED.getCode()));
        when(tradeBillRepository.updateStatusByBillNoAndMerchantId(any(), any(), any(), any()))
                .thenReturn(1);

        ClearanceFailureOutcome out = txSupport.markFailure(ctx, new RuntimeException("boom"));

        assertTrue(out.updated());
        verify(clearanceTaskRepository, never()).findByBillNoAndMerchantId(any(), any());
        verify(clearanceTaskRepository, never()).findByBillNo(any());
    }
}
