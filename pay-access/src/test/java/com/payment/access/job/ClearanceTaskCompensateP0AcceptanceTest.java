package com.payment.access.job;

import com.payment.api.service.ClearanceTaskService;
import com.payment.calc.support.ClearanceTaskPublisher;
import com.payment.common.enums.BillStatus;
import com.payment.common.enums.TaskStatus;
import com.payment.domain.entity.ClearanceTaskEntity;
import com.payment.domain.entity.TradeBillEntity;
import com.payment.domain.repository.ClearanceTaskRepository;
import com.payment.domain.repository.TradeBillRepository;
import com.payment.mq.config.PayMqProperties;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * P0-3 验收：PENDING 无任务补建；已有 PENDING 任务 MQ 重发；终态任务不触发。
 */
@ExtendWith(MockitoExtension.class)
class ClearanceTaskCompensateP0AcceptanceTest {

    @Mock TradeBillRepository tradeBillRepository;
    @Mock ClearanceTaskRepository clearanceTaskRepository;
    @Mock ClearanceTaskService clearanceTaskService;
    @Mock ClearanceTaskPublisher clearanceTaskPublisher;
    @Mock PayMqProperties payMqProperties;

    @Test
    void missingTask_createdAndRepublishedViaMq() {
        ClearanceTaskCompensateJob job = newJob();
        TradeBillEntity bill = bill("B-MISS", 10001L);
        when(tradeBillRepository.findByStatusAndShardId(eq(BillStatus.PENDING.getCode()), anyInt(), eq(100)))
                .thenAnswer(inv -> ((Integer) inv.getArgument(1)) == 0 ? List.of(bill) : List.of());
        when(clearanceTaskRepository.findByBillNoAndMerchantId("B-MISS", 10001L))
                .thenReturn(Optional.empty())
                .thenReturn(Optional.of(task(TaskStatus.PENDING.getCode())));
        when(payMqProperties.isClearanceViaMq()).thenReturn(true);

        job.compensateMissingTasks();

        verify(clearanceTaskService).createTask("B-MISS", 10001L);
        verify(clearanceTaskPublisher).publish("B-MISS", 10001L);
        verify(clearanceTaskService, never()).executeTask(org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any());
    }

    @Test
    void existingPendingTask_republishOnly_noDuplicateCreate() {
        ClearanceTaskCompensateJob job = newJob();
        TradeBillEntity bill = bill("B-EXIST", 10002L);
        when(tradeBillRepository.findByStatusAndShardId(eq(BillStatus.PENDING.getCode()), anyInt(), eq(100)))
                .thenAnswer(inv -> ((Integer) inv.getArgument(1)) == 0 ? List.of(bill) : List.of());
        when(clearanceTaskRepository.findByBillNoAndMerchantId("B-EXIST", 10002L))
                .thenReturn(Optional.of(task(TaskStatus.PENDING.getCode())));
        when(payMqProperties.isClearanceViaMq()).thenReturn(true);

        job.compensateMissingTasks();

        verify(clearanceTaskService, never()).createTask(org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any());
        verify(clearanceTaskPublisher, times(1)).publish("B-EXIST", 10002L);
    }

    @Test
    void runningTask_notRepublished() {
        ClearanceTaskCompensateJob job = newJob();
        TradeBillEntity bill = bill("B-RUN", 10003L);
        when(tradeBillRepository.findByStatusAndShardId(eq(BillStatus.PENDING.getCode()), anyInt(), eq(100)))
                .thenAnswer(inv -> ((Integer) inv.getArgument(1)) == 0 ? List.of(bill) : List.of());
        when(clearanceTaskRepository.findByBillNoAndMerchantId("B-RUN", 10003L))
                .thenReturn(Optional.of(task(TaskStatus.RUNNING.getCode())));

        job.compensateMissingTasks();

        verify(clearanceTaskService, never()).createTask(org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any());
        verify(clearanceTaskPublisher, never()).publish(org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any());
    }

    private ClearanceTaskCompensateJob newJob() {
        return new ClearanceTaskCompensateJob(
                tradeBillRepository, clearanceTaskRepository, clearanceTaskService,
                clearanceTaskPublisher, payMqProperties, false);
    }

    private static TradeBillEntity bill(String billNo, Long merchantId) {
        TradeBillEntity bill = new TradeBillEntity();
        bill.billNo = billNo;
        bill.merchantId = merchantId;
        return bill;
    }

    private static ClearanceTaskEntity task(int status) {
        ClearanceTaskEntity t = new ClearanceTaskEntity();
        t.status = status;
        return t;
    }
}
