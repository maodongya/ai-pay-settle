package com.payment.mq.job; // MQ 定时监控 Job 包

import com.payment.common.enums.TaskStatus; // 清算任务状态枚举
import com.payment.control.service.AlertService; // 告警服务
import com.payment.domain.repository.ClearanceTaskRepository; // 清算任务仓储
import com.payment.domain.repository.OutboxMessageRepository; // Outbox 仓储
import com.payment.mq.config.MqBacklogProperties; // 积压阈值配置
import com.payment.mq.config.PayMqProperties; // MQ 总开关
import com.payment.mq.support.MqBacklogState; // 运行时熔断状态
import io.micrometer.core.instrument.Gauge; // Gauge 指标
import io.micrometer.core.instrument.MeterRegistry; // Micrometer 注册表
import org.slf4j.Logger; // 日志
import org.slf4j.LoggerFactory; // 日志工厂
import org.springframework.beans.factory.ObjectProvider; // 可选 MeterRegistry
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty; // 条件装配
import org.springframework.scheduling.annotation.Scheduled; // 定时任务
import org.springframework.stereotype.Component; // Spring 组件

/**
 * 周期性检查 Outbox / 清算 Pending 积压，超阈值告警并可打开接入熔断。
 */
@Component // 注册为 Spring Bean
@ConditionalOnProperty(name = "pay.mq.enabled", havingValue = "true") // 仅 MQ 模式启用
public class MqBacklogMonitorJob {

    private static final Logger log = LoggerFactory.getLogger(MqBacklogMonitorJob.class); // 日志记录器

    public static final String ALERT_BACKLOG = "MQ_BACKLOG"; // 积压告警类型常量
    public static final String ALERT_OUTBOX = "OUTBOX_BACKLOG"; // Outbox 积压告警类型

    private final PayMqProperties payMqProperties; // MQ 开关
    private final MqBacklogProperties backlogProperties; // 阈值配置
    private final OutboxMessageRepository outboxMessageRepository; // Outbox 计数
    private final ClearanceTaskRepository clearanceTaskRepository; // 清算 Pending 计数
    private final AlertService alertService; // 写 alert_record
    private final MqBacklogState backlogState; // 熔断状态
    private final MeterRegistry meterRegistry; // 可选 Gauge

    /**
     * 构造注入依赖；MeterRegistry 可选。
     */
    public MqBacklogMonitorJob(PayMqProperties payMqProperties,
                               MqBacklogProperties backlogProperties,
                               OutboxMessageRepository outboxMessageRepository,
                               ClearanceTaskRepository clearanceTaskRepository,
                               AlertService alertService,
                               MqBacklogState backlogState,
                               ObjectProvider<MeterRegistry> meterRegistryProvider) {
        this.payMqProperties = payMqProperties; // 保存 MQ 配置
        this.backlogProperties = backlogProperties; // 保存阈值配置
        this.outboxMessageRepository = outboxMessageRepository; // 保存 Outbox 仓储
        this.clearanceTaskRepository = clearanceTaskRepository; // 保存清算仓储
        this.alertService = alertService; // 保存告警服务
        this.backlogState = backlogState; // 保存熔断状态
        this.meterRegistry = meterRegistryProvider.getIfAvailable(); // 可能为 null
        registerGaugesIfNeeded(); // 注册 Gauge（若存在 MeterRegistry）
    }

    /** 注册 Outbox / Pending Gauge（仅首次） */
    private void registerGaugesIfNeeded() {
        if (meterRegistry == null) { // 无 Actuator
            return; // 跳过 Gauge
        }
        Gauge.builder("pay_outbox_pending", outboxMessageRepository, r -> r.countByStatus(0)) // 待发送 Outbox 行数
                .description("Outbox pending message count") // 指标描述
                .register(meterRegistry); // 注册到 Micrometer
        Gauge.builder("pay_clearance_pending", clearanceTaskRepository, r -> r.countByStatus(TaskStatus.PENDING.getCode())) // PENDING 任务数
                .description("Clearance task pending count") // 指标描述
                .register(meterRegistry); // 注册到 Micrometer
    }

    /**
     * 定时巡检积压并告警/熔断。
     */
    @Scheduled(fixedDelayString = "${pay.mq.backlog.check-interval-ms:60000}") // 默认 60 秒
    public void checkBacklog() {
        if (!payMqProperties.isEnabled()) { // MQ 未开启
            return; // 跳过巡检
        }
        long outboxPending = outboxMessageRepository.countByStatus(0); // 统计待发送 Outbox
        long clearancePending = clearanceTaskRepository.countByStatus(TaskStatus.PENDING.getCode()); // 统计 PENDING 清算
        log.debug("backlog check outboxPending={} clearancePending={}", outboxPending, clearancePending); // 调试日志

        boolean critical = false; // 是否达到严重阈值
        StringBuilder summary = new StringBuilder(); // 构建摘要

        if (outboxPending >= backlogProperties.getOutboxPendingCritical()) { // Outbox 严重积压
            critical = true; // 标记严重
            summary.append("outboxCritical=").append(outboxPending).append("; "); // 追加摘要
            alertService.send(ALERT_OUTBOX, AlertService.LEVEL_ERROR, // P1 告警
                    "Outbox pending critical: " + outboxPending); // 告警内容
        } else if (outboxPending >= backlogProperties.getOutboxPendingWarn()) { // Outbox 预警
            summary.append("outboxWarn=").append(outboxPending).append("; "); // 追加摘要
            alertService.send(ALERT_OUTBOX, AlertService.LEVEL_WARN, // P3 预警
                    "Outbox pending warn: " + outboxPending); // 告警内容
        }

        if (clearancePending >= backlogProperties.getClearancePendingWarn()) { // 清算 Pending 预警
            summary.append("clearancePending=").append(clearancePending).append("; "); // 追加摘要
            alertService.send(ALERT_BACKLOG, AlertService.LEVEL_WARN, // 预警
                    "Clearance pending warn: " + clearancePending); // 告警内容
        }

        if (critical && backlogProperties.isCircuitEnabled()) { // 严重且启用熔断
            backlogState.openCircuit(summary.toString()); // 打开接入熔断
            log.warn("mq backlog circuit OPEN: {}", summary); // 记录熔断
        } else { // 未达严重或未启用熔断
            backlogState.closeCircuit(); // 关闭熔断
        }
    }
}
