package com.payment.calc.metrics; // calc 监控采集包

import com.payment.common.enums.TaskStatus; // 清算任务状态枚举
import com.payment.domain.repository.ClearanceTaskRepository; // 清算任务仓储
import io.micrometer.core.instrument.Gauge; // 瞬时值 Gauge 指标
import io.micrometer.core.instrument.MeterRegistry; // Micrometer 注册表
import org.slf4j.Logger; // 日志接口
import org.slf4j.LoggerFactory; // 日志工厂
import org.springframework.beans.factory.ObjectProvider; // 可选 Bean 注入
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty; // 条件装配
import org.springframework.scheduling.annotation.Scheduled; // 定时任务
import org.springframework.stereotype.Component; // Spring 组件

/**
 * 清算任务积压与执行中任务数监控（Prometheus Gauge + 周期日志）。
 */
@Component // 注册为 Spring Bean
@ConditionalOnProperty(name = "pay.mq.enabled", havingValue = "true") // 仅 MQ 模式启用
public class ClearanceMetricsCollector {

    private static final Logger log = LoggerFactory.getLogger(ClearanceMetricsCollector.class); // 本类日志

    private final ClearanceTaskRepository clearanceTaskRepository; // 清算任务仓储
    private final MeterRegistry meterRegistry; // 指标注册表（可能为 null）

    /**
     * 构造注入仓储与可选 MeterRegistry，并注册 Gauge。
     */
    public ClearanceMetricsCollector(ClearanceTaskRepository clearanceTaskRepository,
                                     ObjectProvider<MeterRegistry> meterRegistryProvider) {
        this.clearanceTaskRepository = clearanceTaskRepository; // 赋值仓储
        this.meterRegistry = meterRegistryProvider.getIfAvailable(); // 获取注册表
        registerGauges(); // 注册 Gauge 指标
    }

    /** 注册 RUNNING / FAILED / DEAD 任务数 Gauge */
    private void registerGauges() {
        if (meterRegistry == null) { // 无 Micrometer
            return; // 跳过注册
        }
        Gauge.builder("pay_clearance_running", clearanceTaskRepository, // 执行中任务数
                        r -> r.countByStatus(TaskStatus.RUNNING.getCode())) // 统计 RUNNING
                .description("Clearance tasks in RUNNING state") // 指标描述
                .register(meterRegistry); // 注册
        Gauge.builder("pay_clearance_failed", clearanceTaskRepository, // 失败待重试任务数
                        r -> r.countByStatus(TaskStatus.FAILED.getCode())) // 统计 FAILED
                .description("Clearance tasks in FAILED state (awaiting retry)") // 指标描述
                .register(meterRegistry); // 注册
        Gauge.builder("pay_clearance_dead", clearanceTaskRepository, // 死信任务数
                        r -> r.countByStatus(TaskStatus.DEAD.getCode())) // 统计 DEAD
                .description("Clearance tasks marked DEAD") // 指标描述
                .register(meterRegistry); // 注册
    }

    /**
     * 周期性输出积压快照；积压较高时用 INFO 便于运维检索。
     */
    @Scheduled(fixedDelayString = "${pay.calc.metrics-interval-ms:30000}") // 默认 30 秒
    public void logSnapshot() {
        long pending = clearanceTaskRepository.countByStatus(TaskStatus.PENDING.getCode()); // PENDING 数量
        long running = clearanceTaskRepository.countByStatus(TaskStatus.RUNNING.getCode()); // RUNNING 数量
        long failed = clearanceTaskRepository.countByStatus(TaskStatus.FAILED.getCode()); // FAILED 数量
        if (running > 50 || pending > 500) { // 积压超阈值
            log.info("calc backlog snapshot pending={} running={} failed={}", pending, running, failed); // INFO 日志
        } else { // 正常水位
            log.debug("calc backlog snapshot pending={} running={} failed={}", pending, running, failed); // DEBUG 日志
        }
    }
}
