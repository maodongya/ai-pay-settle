package com.payment.access.job;

import com.payment.api.service.ClearanceTaskService;
import com.payment.calc.support.ClearanceTaskPublisher;
import com.payment.common.enums.BillStatus;
import com.payment.common.enums.TaskStatus;
import com.payment.common.shard.ShardConstants;
import com.payment.domain.entity.TradeBillEntity;
import com.payment.domain.repository.ClearanceTaskRepository;
import com.payment.domain.repository.TradeBillRepository;
import com.payment.mq.config.PayMqProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * 接入补偿：PENDING 账单缺少 clearance_task 时补建任务并触发清算。
 */
@Component
public class ClearanceTaskCompensateJob {

    private static final Logger log = LoggerFactory.getLogger(ClearanceTaskCompensateJob.class);
    private static final int SCAN_PER_SHARD = 100;

    private final TradeBillRepository tradeBillRepository;
    private final ClearanceTaskRepository clearanceTaskRepository;
    private final ClearanceTaskService clearanceTaskService;
    private final ClearanceTaskPublisher clearanceTaskPublisher;
    private final PayMqProperties payMqProperties;
    private final boolean pauseJobs;

    public ClearanceTaskCompensateJob(TradeBillRepository tradeBillRepository,
                                      ClearanceTaskRepository clearanceTaskRepository,
                                      ClearanceTaskService clearanceTaskService,
                                      ClearanceTaskPublisher clearanceTaskPublisher,
                                      PayMqProperties payMqProperties,
                                      @Value("${pay.loadtest.pause-jobs:false}") boolean pauseJobs) {
        this.tradeBillRepository = tradeBillRepository;
        this.clearanceTaskRepository = clearanceTaskRepository;
        this.clearanceTaskService = clearanceTaskService;
        this.clearanceTaskPublisher = clearanceTaskPublisher;
        this.payMqProperties = payMqProperties;
        this.pauseJobs = pauseJobs;
    }

    @Scheduled(fixedDelayString = "${pay.compensate.clearance-task-interval-ms:60000}")
    public void compensateMissingTasks() {
        if (pauseJobs) {
            return;
        }
        int created = 0;
        int republished = 0;
        for (int shardId = 0; shardId < ShardConstants.SHARD_COUNT; shardId++) {
            for (TradeBillEntity bill : tradeBillRepository.findByStatusAndShardId(
                    BillStatus.PENDING.getCode(), shardId, SCAN_PER_SHARD)) {
                boolean existed = clearanceTaskRepository
                        .findByBillNoAndMerchantId(bill.billNo, bill.merchantId)
                        .isPresent();
                if (!existed) {
                    clearanceTaskService.createTask(bill.billNo, bill.merchantId);
                    created++;
                }
                var taskOpt = clearanceTaskRepository.findByBillNoAndMerchantId(bill.billNo, bill.merchantId);
                if (taskOpt.isEmpty()) {
                    continue;
                }
                if (taskOpt.get().status != TaskStatus.PENDING.getCode()) {
                    continue;
                }
                if (payMqProperties.isClearanceViaMq()) {
                    clearanceTaskPublisher.publish(bill.billNo, bill.merchantId);
                    republished++;
                } else if (!existed) {
                    clearanceTaskService.executeTask(bill.billNo, bill.merchantId);
                }
            }
        }
        if (created > 0 || republished > 0) {
            log.info("clearance_task compensated created={} republished={}", created, republished);
        }
    }
}
