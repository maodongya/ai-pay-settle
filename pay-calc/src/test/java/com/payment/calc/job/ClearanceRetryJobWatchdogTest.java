package com.payment.calc.job;

import com.payment.api.service.ClearanceTaskService;
import com.payment.common.enums.TaskStatus;
import com.payment.domain.entity.ClearanceTaskEntity;
import com.payment.domain.repository.ClearanceTaskRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ClearanceRetryJobWatchdogTest {

    @Mock ClearanceTaskService clearanceTaskService;
    @Mock ClearanceTaskRepository clearanceTaskRepository;

    @Test
    void watchdog_delegatesToService_notRepositorySave() {
        ClearanceRetryJob job = new ClearanceRetryJob(clearanceTaskService, clearanceTaskRepository, false);
        ClearanceTaskEntity stale = new ClearanceTaskEntity();
        stale.billNo = "B1";
        stale.merchantId = 10001L;
        when(clearanceTaskRepository.findByStatusAndShardIdAndUpdateTimeBefore(
                eq(TaskStatus.RUNNING.getCode()), anyInt(), any(), anyInt()))
                .thenAnswer(invocation -> {
                    int shardId = invocation.getArgument(1);
                    return shardId == 0 ? List.of(stale) : List.of();
                });

        job.watchdog();

        verify(clearanceTaskService).watchdogFailAndNotify("B1", 10001L);
        verify(clearanceTaskRepository, never()).save(any());
    }
}
