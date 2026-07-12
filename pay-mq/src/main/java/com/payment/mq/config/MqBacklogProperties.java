package com.payment.mq.config; // MQ 积压监控配置类所在包

import org.springframework.boot.context.properties.ConfigurationProperties; // 绑定 yml 配置前缀

/**
 * MQ 与 Outbox 积压监控阈值配置，供 {@link com.payment.mq.job.MqBacklogMonitorJob} 使用。
 */
@ConfigurationProperties(prefix = "pay.mq.backlog") // 绑定 pay.mq.backlog.* 配置项
public class MqBacklogProperties {

    /** 监控 Job 执行间隔（毫秒） */
    private long checkIntervalMs = 60_000L; // 默认每 60 秒巡检一次

    /** Lag 预警阈值（条） */
    private long lagWarn = 1_000L; // P3：超过 1000 条持续观察

    /** Lag 严重阈值（条） */
    private long lagCritical = 10_000L; // P2：超过 10000 条启动 playbook

    /** Outbox 待发送预警阈值（行） */
    private long outboxPendingWarn = 1_000L; // EX-0302 预警线

    /** Outbox 待发送严重阈值（行） */
    private long outboxPendingCritical = 5_000L; // P1：Outbox 严重积压

    /** 清算任务 PENDING 预警阈值（行） */
    private long clearancePendingWarn = 500L; // 业务 Pending 积压预警

    /** 是否在积压严重时打开接入层熔断（HTTP 503） */
    private boolean circuitEnabled = true; // 默认开启熔断保护 DB

    /** 获取监控间隔毫秒数 */
    public long getCheckIntervalMs() {
        return checkIntervalMs; // 返回巡检间隔
    }

    /** 设置监控间隔毫秒数 */
    public void setCheckIntervalMs(long checkIntervalMs) {
        this.checkIntervalMs = checkIntervalMs; // 写入巡检间隔
    }

    /** 获取 Lag 预警阈值 */
    public long getLagWarn() {
        return lagWarn; // 返回 Lag 预警线
    }

    /** 设置 Lag 预警阈值 */
    public void setLagWarn(long lagWarn) {
        this.lagWarn = lagWarn; // 写入 Lag 预警线
    }

    /** 获取 Lag 严重阈值 */
    public long getLagCritical() {
        return lagCritical; // 返回 Lag 严重线
    }

    /** 设置 Lag 严重阈值 */
    public void setLagCritical(long lagCritical) {
        this.lagCritical = lagCritical; // 写入 Lag 严重线
    }

    /** 获取 Outbox 预警阈值 */
    public long getOutboxPendingWarn() {
        return outboxPendingWarn; // 返回 Outbox 预警线
    }

    /** 设置 Outbox 预警阈值 */
    public void setOutboxPendingWarn(long outboxPendingWarn) {
        this.outboxPendingWarn = outboxPendingWarn; // 写入 Outbox 预警线
    }

    /** 获取 Outbox 严重阈值 */
    public long getOutboxPendingCritical() {
        return outboxPendingCritical; // 返回 Outbox 严重线
    }

    /** 设置 Outbox 严重阈值 */
    public void setOutboxPendingCritical(long outboxPendingCritical) {
        this.outboxPendingCritical = outboxPendingCritical; // 写入 Outbox 严重线
    }

    /** 获取清算 PENDING 预警阈值 */
    public long getClearancePendingWarn() {
        return clearancePendingWarn; // 返回清算 Pending 预警线
    }

    /** 设置清算 PENDING 预警阈值 */
    public void setClearancePendingWarn(long clearancePendingWarn) {
        this.clearancePendingWarn = clearancePendingWarn; // 写入清算 Pending 预警线
    }

    /** 是否启用积压熔断 */
    public boolean isCircuitEnabled() {
        return circuitEnabled; // 返回熔断开关状态
    }

    /** 设置积压熔断开关 */
    public void setCircuitEnabled(boolean circuitEnabled) {
        this.circuitEnabled = circuitEnabled; // 写入熔断开关
    }
}
