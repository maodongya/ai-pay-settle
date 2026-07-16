package com.payment.mq.support;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;

/**
 * MQ 生产侧 TPS 与发送耗时指标。
 */
@Component
public class PayMqProduceMetrics {

    private final MeterRegistry registry;
    private final ConcurrentHashMap<String, Counter> successCounters = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, Counter> failCounters = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, Timer> timers = new ConcurrentHashMap<>();

    /**
     * 构造注入可选 MeterRegistry，无 Actuator 时降级为无指标模式。
     */
    public PayMqProduceMetrics(ObjectProvider<MeterRegistry> registryProvider) {
        this.registry = registryProvider.getIfAvailable();
    }

    /**
     * 包裹生产动作，记录成功/失败计数与发送耗时。
     */
    public void record(String topic, Runnable action) {
        if (registry == null) {
            action.run();
            return;
        }
        long start = System.nanoTime();
        try {
            action.run();
            successCounter(topic).increment();
        } catch (RuntimeException e) {
            failCounter(topic).increment();
            throw e;
        } finally {
            timers.computeIfAbsent(topic, t -> registry.timer("pay_mq_produce_duration", "topic", t))
                    .record(System.nanoTime() - start, TimeUnit.NANOSECONDS);
        }
    }

    /**
     * 记录 Outbox 派发成败计数。
     */
    public void recordOutboxDispatch(String topic, boolean success) {
        if (registry == null) {
            return;
        }
        registry.counter("pay_outbox_dispatch_total",
                "topic", topic,
                "status", success ? "success" : "fail").increment();
    }

    private Counter successCounter(String topic) {
        return successCounters.computeIfAbsent(topic,
                t -> registry.counter("pay_mq_produce_total", "topic", t, "status", "success"));
    }

    private Counter failCounter(String topic) {
        return failCounters.computeIfAbsent(topic,
                t -> registry.counter("pay_mq_produce_total", "topic", t, "status", "fail"));
    }
}
