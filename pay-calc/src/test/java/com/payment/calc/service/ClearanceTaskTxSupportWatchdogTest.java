package com.payment.calc.service;

import com.payment.api.service.FeeCalcService;
import com.payment.api.service.SplitService;
import com.payment.common.enums.BillStatus;
import com.payment.common.enums.TaskStatus;
import com.payment.domain.entity.ClearanceTaskEntity;
import com.payment.domain.repository.ClearanceTaskRepository;
import com.payment.domain.repository.TradeBillRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
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
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ClearanceTaskTxSupportWatchdogTest {

    @Mock ClearanceTaskRepository clearanceTaskRepository;
    @Mock TradeBillRepository tradeBillRepository;
    @Mock FeeCalcService feeCalcService;
    @Mock SplitService splitService;
    @InjectMocks ClearanceTaskTxSupport txSupport;

    @Test
    void watchdogFail_updatesTaskAndBill_whenRunning() {
        ClearanceTaskEntity task = new ClearanceTaskEntity();
        task.retryCount = 0;
        when(clearanceTaskRepository.findByBillNoAndMerchantId("B1", 10001L))
                .thenReturn(Optional.of(task));
        when(clearanceTaskRepository.markFailed(
                eq("B1"), eq(10001L), eq(TaskStatus.RUNNING.getCode()),
                eq(TaskStatus.FAILED.getCode()), eq(TaskStatus.DEAD.getCode()),
                anyInt(), eq("watchdog timeout"), any(LocalDateTime.class), any(LocalDateTime.class)))
                .thenReturn(1);
        when(tradeBillRepository.updateStatusByBillNoAndMerchantId(
                "B1", 10001L, BillStatus.CLEARING.getCode(), BillStatus.FAILED.getCode()))
                .thenReturn(1);

        ClearanceFailureOutcome out = txSupport.watchdogFail("B1", 10001L);

        assertTrue(out.updated());
        assertFalse(out.enteredDead());
        verify(tradeBillRepository).updateStatusByBillNoAndMerchantId(
                "B1", 10001L, BillStatus.CLEARING.getCode(), BillStatus.FAILED.getCode());
    }

    @Test
    void watchdogFail_skipsBill_whenTaskCasMisses() {
        ClearanceTaskEntity task = new ClearanceTaskEntity();
        task.retryCount = 0;
        when(clearanceTaskRepository.findByBillNoAndMerchantId("B1", 10001L))
                .thenReturn(Optional.of(task));
        when(clearanceTaskRepository.markFailed(any(), any(), any(), any(), any(),
                anyInt(), any(), any(), any())).thenReturn(0);

        ClearanceFailureOutcome out = txSupport.watchdogFail("B1", 10001L);

        assertFalse(out.updated());
        verify(tradeBillRepository, never()).updateStatusByBillNoAndMerchantId(
                any(), any(), any(), any());
    }
}
