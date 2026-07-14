package com.payment.access.job;

import com.payment.domain.entity.TradeBillEntity;
import com.payment.domain.repository.TradeBillRepository;
import com.payment.domain.service.ShardRouteService;
import com.payment.domain.support.ShardScanSupport;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Saga 补偿：data 层有 trade_bill 但 config 层缺少 bill_route 时补写路由索引。
 */
@Component
public class BillRouteCompensateJob {

    private static final Logger log = LoggerFactory.getLogger(BillRouteCompensateJob.class);
    private static final int SCAN_PER_SHARD = 200;

    private final TradeBillRepository tradeBillRepository;
    private final ShardRouteService shardRouteService;
    private final boolean pauseJobs;

    public BillRouteCompensateJob(TradeBillRepository tradeBillRepository,
                                  ShardRouteService shardRouteService,
                                  @Value("${pay.loadtest.pause-jobs:false}") boolean pauseJobs) {
        this.tradeBillRepository = tradeBillRepository;
        this.shardRouteService = shardRouteService;
        this.pauseJobs = pauseJobs;
    }

    @Scheduled(fixedDelayString = "${pay.compensate.bill-route-interval-ms:600000}")
    public void compensateMissingRoutes() {
        if (pauseJobs) {
            return;
        }
        int compensated = 0;
        for (int shardId = 0; shardId < com.payment.common.shard.ShardConstants.SHARD_COUNT; shardId++) {
            compensated += compensateShard(shardId);
        }
        if (compensated > 0) {
            log.info("bill_route compensated count={}", compensated);
        }
    }

    private int compensateShard(int shardId) {
        int count = 0;
        for (TradeBillEntity bill : tradeBillRepository.findRecentByShardId(shardId, SCAN_PER_SHARD)) {
            if (shardRouteService.findMerchantIdByBillNo(bill.billNo).isPresent()) {
                continue;
            }
            shardRouteService.registerBillRoute(bill.billNo, bill.merchantId, bill.billType);
            count++;
        }
        return count;
    }
}
