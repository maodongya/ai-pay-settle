package com.payment.common.metrics;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

/**
 * 业务吞吐与端到端耗时指标。
 */
@Component
public class PayBusinessMetrics {

    public static final String STAGE_BILL_ACCEPT = "bill_accept";
    public static final String STAGE_CLEARANCE_DONE = "clearance_done";
    public static final String STAGE_SPLIT_DONE = "split_done";
    public static final String STAGE_SETTLE_DONE = "settle_done";
    public static final String STAGE_PAYMENT_DONE = "payment_done";

    private final MeterRegistry registry;
    private final ConcurrentHashMap<String, Instant> e2eStarts = new ConcurrentHashMap<>();

    public PayBusinessMetrics(ObjectProvider<MeterRegistry> registryProvider) {
        this.registry = registryProvider.getIfAvailable();
    }

    /** 记录业务阶段吞吐 */
    public void recordThroughput(String stage, boolean success) {
        if (registry == null) {
            return;
        }
        registry.counter("pay_business_throughput_total",
                "stage", stage,
                "status", success ? "success" : "fail").increment();
    }

    /** 账单接入时记录端到端起点 */
    public void markBillAccepted(String billNo, int billType) {
        if (registry == null) {
            return;
        }
        registry.counter("pay_business_throughput_total",
                "stage", STAGE_BILL_ACCEPT,
                "status", "success",
                "bill_type", String.valueOf(billType)).increment();
        if (billNo != null) {
            e2eStarts.put(billNo, Instant.now());
        }
    }

    /** 分账完成时结算端到端耗时 */
    public void recordSplitDone(String billNo, int billType) {
        recordThroughput(STAGE_SPLIT_DONE, true);
        if (registry == null || billNo == null) {
            return;
        }
        Instant start = e2eStarts.remove(billNo);
        if (start == null) {
            return;
        }
        long millis = Duration.between(start, Instant.now()).toMillis();
        e2eTimer(billType).record(millis, TimeUnit.MILLISECONDS);
    }

    private Timer e2eTimer(int billType) {
        return registry.timer("pay_e2e_duration", "bill_type", String.valueOf(billType));
    }
}
