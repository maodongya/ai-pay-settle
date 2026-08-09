package com.payment.calc.service;

import com.payment.api.service.MerchantValidateService;
import com.payment.calc.metrics.ClearanceTaskMetrics;
import com.payment.calc.support.ClearanceTaskPublisher;
import com.payment.common.enums.TaskStatus;
import com.payment.common.metrics.PayBusinessMetrics;
import com.payment.control.service.AlertService;
import com.payment.control.service.ExceptionRecordService;
import com.payment.domain.entity.TradeBillEntity;
import com.payment.domain.repository.ClearanceTaskRepository;
import com.payment.domain.repository.TradeBillRepository;
import com.payment.domain.service.ShardRouteService;
import com.payment.mq.config.PayMqProperties;
import com.payment.mq.exception.NonRetryableException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DeadlockLoserDataAccessException;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * C2/C4：终态 DEAD NonRetryable；claim 死锁有限重试。
 */
@ExtendWith(MockitoExtension.class)
class ClearanceC2C4AcceptanceTest {

    @Mock ClearanceTaskRepository clearanceTaskRepository;
    @Mock TradeBillRepository tradeBillRepository;
    @Mock ShardRouteService shardRouteService;
    @Mock MerchantValidateService merchantValidateService;
    @Mock PayMqProperties payMqProperties;
    @Mock ClearanceTaskPublisher clearanceTaskPublisher;
    @Mock PayBusinessMetrics businessMetrics;
    @Mock ClearanceTaskTxSupport clearanceTaskTxSupport;
    @Mock ClearanceTaskMetrics clearanceTaskMetrics;
    @Mock ExceptionRecordService exceptionRecordService;
    @Mock AlertService alertService;

    ClearanceTaskServiceImpl service;

    @BeforeEach
    void setUp() {
        service = new ClearanceTaskServiceImpl(
                clearanceTaskRepository, tradeBillRepository, shardRouteService,
                merchantValidateService, payMqProperties, clearanceTaskPublisher,
                businessMetrics, clearanceTaskTxSupport, clearanceTaskMetrics,
                exceptionRecordService, alertService);
        when(clearanceTaskMetrics.nanoTime()).thenReturn(1L);
    }

    @Test
    void executeTask_deadTerminal_throwsNonRetryable() {
        when(clearanceTaskRepository.findStatusByBillNoAndMerchantId("B-DEAD", 10001L))
                .thenReturn(Optional.of(TaskStatus.DEAD.getCode()));

        assertThrows(NonRetryableException.class, () -> service.executeTask("B-DEAD", 10001L));
        verify(clearanceTaskMetrics).recordSkip(ClearanceTaskMetrics.SKIP_REASON_TERMINAL);
        verify(clearanceTaskTxSupport, times(0)).claimAndMarkClearing(anyString(), anyLong());
    }

    @Test
    void executeTask_successTerminal_acksWithoutClaim() {
        when(clearanceTaskRepository.findStatusByBillNoAndMerchantId("B-OK", 10001L))
                .thenReturn(Optional.of(TaskStatus.SUCCESS.getCode()));

        assertDoesNotThrow(() -> service.executeTask("B-OK", 10001L));
        verify(clearanceTaskMetrics).recordSkip(ClearanceTaskMetrics.SKIP_REASON_TERMINAL);
        verify(clearanceTaskTxSupport, times(0)).claimAndMarkClearing(anyString(), anyLong());
    }

    @Test
    void claim_retriesOnce_onDeadlockThenSucceeds() {
        when(clearanceTaskRepository.findStatusByBillNoAndMerchantId("B-DL", 10001L))
                .thenReturn(Optional.of(TaskStatus.PENDING.getCode()));
        TradeBillEntity bill = new TradeBillEntity();
        bill.billNo = "B-DL";
        bill.merchantId = 10001L;
        ClearanceClaimContext ctx = new ClearanceClaimContext(bill, 10001L);
        when(clearanceTaskTxSupport.claimAndMarkClearing("B-DL", 10001L))
                .thenThrow(new DeadlockLoserDataAccessException("deadlock", null))
                .thenReturn(Optional.of(ctx));
        when(merchantValidateService.loadRelation(10001L)).thenReturn(new com.payment.api.dto.AgentRelationDTO());
        when(clearanceTaskTxSupport.runFeeAndSplit(any(), any())).thenReturn(null);
        when(tradeBillRepository.findByStatusAndOriginBillNo(any(), anyString()))
                .thenReturn(java.util.List.of());

        assertDoesNotThrow(() -> service.executeTask("B-DL", 10001L));

        verify(clearanceTaskTxSupport, times(2)).claimAndMarkClearing("B-DL", 10001L);
        verify(clearanceTaskMetrics).recordDeadlock();
        verify(clearanceTaskTxSupport).finalizeSuccess(ctx);
    }
}
