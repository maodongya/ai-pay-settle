package com.payment.calc.metrics; // calc 清算指标包

import io.micrometer.core.instrument.Counter; // 计数器指标
import io.micrometer.core.instrument.MeterRegistry; // Micrometer 注册表
import io.micrometer.core.instrument.Timer; // 耗时 Timer 指标
import org.springframework.beans.factory.ObjectProvider; // 可选 Bean 注入
import org.springframework.stereotype.Component; // Spring 组件

import java.util.concurrent.ConcurrentHashMap; // 线程安全 Map 缓存 Timer
import java.util.concurrent.TimeUnit; // 时间单位

/**
 * 清算（calc）链路 Micrometer 指标：消费耗时、分阶段耗时、成败计数。
 */
@Component // 注册为 Spring 单例
public class ClearanceTaskMetrics {

    public static final String STAGE_CLAIM = "claim"; // 阶段：抢占任务
    public static final String STAGE_FEE_SPLIT = "fee_split"; // 阶段：计费+分账
    public static final String STAGE_FINALIZE = "finalize"; // 阶段：落库成功态
    public static final String STAGE_FAIL = "fail"; // 阶段：失败处理

    private final MeterRegistry registry; // 指标注册表（可能为 null）
    private final ConcurrentHashMap<String, Timer> stageTimers = new ConcurrentHashMap<>(); // 分阶段 Timer 缓存
    private final Counter successCounter; // 清算成功计数
    private final Counter failCounter; // 清算失败计数
    private final Counter skipCounter; // 幂等跳过计数

    /**
     * 构造：无 MeterRegistry 时降级为无指标模式。
     */
    public ClearanceTaskMetrics(ObjectProvider<MeterRegistry> registryProvider) {
        this.registry = registryProvider.getIfAvailable(); // 获取注册表（可选）
        if (registry != null) { // 存在 Actuator/Micrometer
            successCounter = registry.counter("pay_calc_clearance_total", "status", "success"); // 成功 Counter
            failCounter = registry.counter("pay_calc_clearance_total", "status", "fail"); // 失败 Counter
            skipCounter = registry.counter("pay_calc_clearance_total", "status", "skip"); // 跳过 Counter
        } else { // 无注册表
            successCounter = null; // 不记录成功
            failCounter = null; // 不记录失败
            skipCounter = null; // 不记录跳过
        }
    }

    /**
     * 记录整单清算消费耗时（由 executeTask 入口调用）。
     */
    public void recordConsume(long startNanos, boolean success) {
        if (registry == null) { // 无指标
            return; // 直接返回
        }
        registry.timer("pay_calc_consume_duration") // 整单耗时 Timer
                .record(System.nanoTime() - startNanos, TimeUnit.NANOSECONDS); // 写入纳秒耗时
        if (success) { // 清算成功
            successCounter.increment(); // 成功 +1
        } else { // 清算失败或未成功完成
            failCounter.increment(); // 失败 +1
        }
    }

    /** 记录幂等跳过（抢占失败等） */
    public void recordSkip() {
        if (skipCounter != null) { // Counter 可用
            skipCounter.increment(); // 跳过 +1
        }
    }

    /**
     * 分阶段耗时：claim / fee_split / finalize / fail。
     */
    public void recordStage(String stage, long startNanos) {
        if (registry == null) { // 无指标
            return; // 直接返回
        }
        stageTimers.computeIfAbsent(stage, s -> // 按阶段懒创建 Timer
                registry.timer("pay_calc_stage_duration", "stage", s)) // 指标名 + stage 标签
                .record(System.nanoTime() - startNanos, TimeUnit.NANOSECONDS); // 写入阶段耗时
    }

    /** 获取纳秒时间戳，供调用方计时 */
    public long nanoTime() {
        return System.nanoTime(); // 当前纳秒
    }
}
