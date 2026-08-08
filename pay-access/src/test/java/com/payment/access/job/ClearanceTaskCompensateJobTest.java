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
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ClearanceTaskCompensateJobTest {

    @Mock TradeBillRepository tradeBillRepository;
    @Mock ClearanceTaskRepository clearanceTaskRepository;
    @Mock ClearanceTaskService clearanceTaskService;
    @Mock ClearanceTaskPublisher clearanceTaskPublisher;
    @Mock PayMqProperties payMqProperties;

    @Test
    void createsTask_whenPendingBillHasNoTask_andTriggersSyncExecute() {
        ClearanceTaskCompensateJob job = new ClearanceTaskCompensateJob(
                tradeBillRepository, clearanceTaskRepository, clearanceTaskService,
                clearanceTaskPublisher, payMqProperties, false);

        TradeBillEntity bill = new TradeBillEntity();
        bill.billNo = "B1";
        bill.merchantId = 10001L;
        when(tradeBillRepository.findByStatusAndShardId(eq(BillStatus.PENDING.getCode()), anyInt(), eq(100)))
                .thenAnswer(invocation -> {
                    int shardId = invocation.getArgument(1);
                    return shardId == 0 ? List.of(bill) : List.of();
                });
        when(clearanceTaskRepository.findByBillNoAndMerchantId("B1", 10001L))
                .thenReturn(Optional.empty())
                .thenReturn(Optional.of(task(TaskStatus.PENDING.getCode())));
        when(payMqProperties.isClearanceViaMq()).thenReturn(false);

        job.compensateMissingTasks();

        verify(clearanceTaskService).createTask("B1", 10001L);
        verify(clearanceTaskService).executeTask("B1", 10001L);
        verify(clearanceTaskPublisher, never()).publish(org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any());
    }

    private static ClearanceTaskEntity task(int status) {
        ClearanceTaskEntity t = new ClearanceTaskEntity();
        t.status = status;
        return t;
    }
}
