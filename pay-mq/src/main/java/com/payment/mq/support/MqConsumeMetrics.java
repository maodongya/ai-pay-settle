package com.payment.mq.support; // MQ 消费指标埋点包

import io.micrometer.core.instrument.Counter; // 计数器指标
import io.micrometer.core.instrument.MeterRegistry; // Micrometer 注册表
import io.micrometer.core.instrument.Timer; // 耗时指标
import org.springframework.beans.factory.ObjectProvider; // 可选依赖注入
import org.springframework.stereotype.Component; // Spring 组件

import java.util.concurrent.ConcurrentHashMap; // 线程安全缓存 Counter/Timer
import java.util.concurrent.TimeUnit; // 时间单位

/**
 * MQ 消费 Micrometer 指标：次数、耗时。无 MeterRegistry 时降级为无指标模式。
 */
@Component // 全局单例，Listener 代理层调用
public class MqConsumeMetrics {

    private final MeterRegistry registry; // 可能为 null（未引入 Actuator 时）
    private final ConcurrentHashMap<String, Counter> successCounters = new ConcurrentHashMap<>(); // 成功计数缓存
    private final ConcurrentHashMap<String, Counter> failCounters = new ConcurrentHashMap<>(); // 失败计数缓存
    private final ConcurrentHashMap<String, Timer> timers = new ConcurrentHashMap<>(); // 耗时 Timer 缓存

    /**
     * 通过 ObjectProvider 可选注入 MeterRegistry，避免无 Actuator 时启动失败。
     */
    public MqConsumeMetrics(ObjectProvider<MeterRegistry> registryProvider) {
        this.registry = registryProvider.getIfAvailable(); // 无 Bean 时为 null
    }

    /**
     * 记录无返回值消费动作的耗时与成败。
     *
     * @param topic  Topic 标签
     * @param action 业务 Runnable
     */
    public void recordVoid(String topic, Runnable action) {
        long start = System.nanoTime(); // 记录开始纳秒时间
        try { // 执行业务
            action.run(); // 调用 delegate.handle
            markSuccess(topic); // 成功计数 +1
        } catch (RuntimeException e) { // 仅捕获运行时异常（Listener 约定）
            markFail(topic); // 失败计数 +1
            throw e; // 继续向上抛给 Invoker 分类
        } finally { // 无论成败记录耗时
            recordDuration(topic, start); // 写入 Timer
        }
    }

    /** 成功计数 +1 */
    private void markSuccess(String topic) {
        if (registry == null) { // 无 Micrometer
            return; // 跳过指标
        }
        successCounter(topic).increment(); // Counter 递增
    }

    /** 失败计数 +1 */
    private void markFail(String topic) {
        if (registry == null) { // 无 Micrometer
            return; // 跳过指标
        }
        failCounter(topic).increment(); // Counter 递增
    }

    /** 记录耗时到 Timer */
    private void recordDuration(String topic, long startNanos) {
        if (registry == null) { // 无 Micrometer
            return; // 跳过指标
        }
        long elapsed = System.nanoTime() - startNanos; // 计算耗时纳秒
        timer(topic).record(elapsed, TimeUnit.NANOSECONDS); // 写入 Timer
    }

    /** 获取或创建成功 Counter */
    private Counter successCounter(String topic) {
        return successCounters.computeIfAbsent(topic, t -> registry.counter("pay_mq_consume_total", "topic", t, "status", "success")); // 懒创建
    }

    /** 获取或创建失败 Counter */
    private Counter failCounter(String topic) {
        return failCounters.computeIfAbsent(topic, t -> registry.counter("pay_mq_consume_total", "topic", t, "status", "fail")); // 懒创建
    }

    /** 获取或创建 Timer */
    private Timer timer(String topic) {
        return timers.computeIfAbsent(topic, t -> registry.timer("pay_mq_consume_duration", "topic", t)); // 懒创建
    }
}
