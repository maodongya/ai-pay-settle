package com.payment.settlement.job;

import com.payment.api.dto.PaymentCallbackDTO;
import com.payment.api.service.SettleAccountService;
import com.payment.common.enums.SettleOrderStatus;
import com.payment.domain.entity.SettlementOrderEntity;
import com.payment.domain.repository.SettlementOrderEntityRepository;
import com.payment.settlement.channel.MockPaymentChannel;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;

/**
 * R2：扫描超时仍为 PAYING 的结算单，向渠道查单并走统一回调路径。
 */
@Component
public class PaymentStatusQueryJob {

    private static final Logger log = LoggerFactory.getLogger(PaymentStatusQueryJob.class);

    private final SettlementOrderEntityRepository settlementOrderRepository;
    private final MockPaymentChannel paymentChannel;
    private final SettleAccountService settleAccountService;
    private final boolean pauseJobs;
    private final int batchLimit;
    private final int staleMinutes;

    public PaymentStatusQueryJob(SettlementOrderEntityRepository settlementOrderRepository,
                                 MockPaymentChannel paymentChannel,
                                 SettleAccountService settleAccountService,
                                 @Value("${pay.loadtest.pause-jobs:false}") boolean pauseJobs,
                                 @Value("${pay.settlement.paying-query-limit:50}") int batchLimit,
                                 @Value("${pay.settlement.paying-stale-minutes:5}") int staleMinutes) {
        this.settlementOrderRepository = settlementOrderRepository;
        this.paymentChannel = paymentChannel;
        this.settleAccountService = settleAccountService;
        this.pauseJobs = pauseJobs;
        this.batchLimit = batchLimit;
        this.staleMinutes = staleMinutes;
    }

    @Scheduled(fixedDelayString = "${pay.settlement.paying-query-interval-ms:60000}")
    public void queryStalePayingOrders() {
        if (pauseJobs) {
            return;
        }
        LocalDateTime threshold = LocalDateTime.now().minusMinutes(staleMinutes);
        List<SettlementOrderEntity> paying = settlementOrderRepository.findTopNByStatus(
                SettleOrderStatus.PAYING.getCode(), batchLimit);
        int queried = 0;
        for (SettlementOrderEntity order : paying) {
            if (order.updateTime != null && order.updateTime.isAfter(threshold)) {
                continue; // 未超时，等渠道自然回调
            }
            try {
                PaymentCallbackDTO callback = paymentChannel.queryStatus(order.settleNo);
                if (callback == null || callback.status == null) {
                    continue;
                }
                settleAccountService.handlePaymentCallback(callback);
                queried++;
            } catch (Exception e) {
                log.warn("paying query failed settleNo={}", order.settleNo, e);
            }
        }
        if (queried > 0) {
            log.info("paying status query finished count={}", queried);
        }
    }
}
