package com.payment.mq.job;

import com.payment.control.service.AlertService;
import com.payment.mq.MqTopics;
import com.payment.mq.config.PayMqProperties;
import com.payment.mq.config.TpsMonitorProperties;
import com.payment.mq.support.MqBacklogState;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.search.Search;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicReference;

/**
 * 周期性对照 MQ 生产/消费 TPS，写入 Gap Gauge 并告警。
 */
@Component
@ConditionalOnProperty(name = "pay.monitor.tps.enabled", havingValue = "true", matchIfMissing = true)
public class TpsBalanceMonitorJob {

    private static final Logger log = LoggerFactory.getLogger(TpsBalanceMonitorJob.class);

    public static final String ALERT_TPS_IMBALANCE = "TPS_IMBALANCE";
    public static final String ALERT_TPS_DROP = "TPS_DROP";

    private static final String[] TOPICS = {
            MqTopics.TRADE_PAY,
            MqTopics.TRADE_REFUND,
            MqTopics.CLEARANCE_TASK,
            MqTopics.SETTLE_AMOUNT,
            MqTopics.PAYMENT_RESULT
    };

    private final PayMqProperties payMqProperties;
    private final TpsMonitorProperties tpsProperties;
    private final AlertService alertService;
    private final MqBacklogState backlogState;
    private final MeterRegistry meterRegistry;
    private final Map<String, CounterSnapshot> lastSnapshots = new ConcurrentHashMap<>();
    private final Map<String, AtomicReference<Double>> gapHolders = new ConcurrentHashMap<>();
    private final AtomicReference<Double> lastTotalConsumeTps = new AtomicReference<>(0.0);

    /**
     * 构造注入 MQ 配置、TPS 阈值、告警服务与 MeterRegistry。
     */
    public TpsBalanceMonitorJob(PayMqProperties payMqProperties,
                                TpsMonitorProperties tpsProperties,
                                AlertService alertService,
                                MqBacklogState backlogState,
                                ObjectProvider<MeterRegistry> meterRegistryProvider) {
        this.payMqProperties = payMqProperties;
        this.tpsProperties = tpsProperties;
        this.alertService = alertService;
        this.backlogState = backlogState;
        this.meterRegistry = meterRegistryProvider.getIfAvailable();
        registerCircuitGauge();
    }

    private void registerCircuitGauge() {
        if (meterRegistry == null) {
            return;
        }
        Gauge.builder("pay_mq_circuit_open", backlogState, s -> s.isCircuitOpen() ? 1.0 : 0.0)
                .description("MQ backlog circuit breaker state")
                .register(meterRegistry);
    }

    /**
     * 定时对照各 Topic 生产/消费 TPS，写入 Gap Gauge 并告警。
     */
    @Scheduled(fixedDelayString = "${pay.monitor.tps.check-interval-ms:60000}")
    public void checkTpsBalance() {
        if (!payMqProperties.isEnabled() || meterRegistry == null) {
            return;
        }
        double totalConsumeTps = 0.0;
        for (String topic : TOPICS) {
            double produceTps = ratePerSecond("pay_mq_produce_total", topic, "success");
            double consumeTps = ratePerSecond("pay_mq_consume_total", topic, "success");
            double gap = Math.max(0.0, produceTps - consumeTps);
            totalConsumeTps += consumeTps;
            updateGapGauge(topic, gap);
            if (gap >= tpsProperties.getGapCritical()) {
                alertService.send(ALERT_TPS_IMBALANCE, AlertService.LEVEL_ERROR,
                        "topic=" + topic + " produceTps=" + produceTps + " consumeTps=" + consumeTps + " gap=" + gap);
            } else if (gap >= tpsProperties.getGapWarn()) {
                alertService.send(ALERT_TPS_IMBALANCE, AlertService.LEVEL_WARN,
                        "topic=" + topic + " gap=" + gap);
            }
        }
        checkConsumeDrop(totalConsumeTps);
    }

    private void updateGapGauge(String topic, double gap) {
        AtomicReference<Double> holder = gapHolders.computeIfAbsent(topic, t -> {
            AtomicReference<Double> ref = new AtomicReference<>(0.0);
            Gauge.builder("pay_tps_produce_consume_gap", ref, r -> r.get() != null ? r.get() : 0.0)
                    .tag("topic", t)
                    .description("Produce minus consume TPS per topic")
                    .register(meterRegistry);
            return ref;
        });
        holder.set(gap);
    }

    private void checkConsumeDrop(double currentTotalConsumeTps) {
        Double previous = lastTotalConsumeTps.getAndSet(currentTotalConsumeTps);
        if (previous == null || previous <= 0) {
            return;
        }
        double dropRatio = 1.0 - (currentTotalConsumeTps / previous);
        if (dropRatio >= tpsProperties.getDropRatioCritical()) {
            alertService.send(ALERT_TPS_DROP, AlertService.LEVEL_ERROR,
                    "consumeTps dropped ratio=" + dropRatio + " prev=" + previous + " now=" + currentTotalConsumeTps);
        } else if (dropRatio >= tpsProperties.getDropRatioWarn()) {
            alertService.send(ALERT_TPS_DROP, AlertService.LEVEL_WARN,
                    "consumeTps dropped ratio=" + dropRatio);
        }
    }

    private double ratePerSecond(String metricName, String topic, String status) {
        double count = Search.in(meterRegistry)
                .name(metricName)
                .tags("topic", topic, "status", status)
                .counters()
                .stream()
                .mapToDouble(c -> c.count())
                .sum();
        long now = System.nanoTime();
        String key = metricName + "|" + topic + "|" + status;
        CounterSnapshot previous = lastSnapshots.put(key, new CounterSnapshot(count, now));
        if (previous == null) {
            return 0.0;
        }
        double delta = count - previous.count;
        double seconds = (now - previous.nanoTime) / 1_000_000_000.0;
        if (seconds <= 0) {
            return 0.0;
        }
        return delta / seconds;
    }

    private record CounterSnapshot(double count, long nanoTime) {
    }
}
