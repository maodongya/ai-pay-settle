package com.payment.mq.config; // MQ 消费者配置类所在包

import org.springframework.boot.context.properties.ConfigurationProperties; // 绑定 application.yml 前缀配置

/**
 * RocketMQ 消费者统一线程与拉取参数配置。
 * 注解 {@link RocketMQMessageListener} 要求编译期常量，因此 yml 与本类默认值需手动保持一致。
 */
@ConfigurationProperties(prefix = "pay.mq.consumer") // 绑定 pay.mq.consumer.* 配置项
public class MqConsumerProperties {

    /** 接入层（trade_pay / trade_refund）最大消费线程数 */
    private int accessThreadMax = 32; // 默认 32，与 application-mq.yml 保持一致

    /** 算账层（clearance_task）最大消费线程数 */
    private int calcThreadMax = 32; // 默认 32，清算 CPU/DB 密集，可独立调优

    /** 结算层（settle_amount）最大消费线程数（ORDERLY 模式下为 Queue 并行度） */
    private int settlementThreadMax = 16; // 默认 16，避免 30005 乐观锁风暴

    /** 支付结果（payment_result）最大消费线程数 */
    private int settlementPaymentThreadMax = 16; // 默认 16，与结算层实例数匹配

    /** 单条消息最长消费时间（分钟），超时 Broker 会重投 */
    private int consumeTimeoutMinutes = 15; // 默认 15 分钟，覆盖慢 SQL 场景

    /** 每次 Pull 批量大小，影响吞吐与内存 */
    private int pullBatchSize = 32; // 默认 32，与 rocketmq.consumer.pull-batch-size 对齐

    /** 消费角色过滤：all / access / calc / settlement，用于拆分部署 */
    private String roles = "all"; // 默认 all 表示单实例启用全部 Consumer

    /** 获取接入层最大消费线程数 */
    public int getAccessThreadMax() {
        return accessThreadMax; // 返回接入层线程上限
    }

    /** 设置接入层最大消费线程数 */
    public void setAccessThreadMax(int accessThreadMax) {
        this.accessThreadMax = accessThreadMax; // 写入接入层线程上限
    }

    /** 获取算账层最大消费线程数 */
    public int getCalcThreadMax() {
        return calcThreadMax; // 返回算账层线程上限
    }

    /** 设置算账层最大消费线程数 */
    public void setCalcThreadMax(int calcThreadMax) {
        this.calcThreadMax = calcThreadMax; // 写入算账层线程上限
    }

    /** 获取结算入账最大消费线程数 */
    public int getSettlementThreadMax() {
        return settlementThreadMax; // 返回结算层线程上限
    }

    /** 设置结算入账最大消费线程数 */
    public void setSettlementThreadMax(int settlementThreadMax) {
        this.settlementThreadMax = settlementThreadMax; // 写入结算层线程上限
    }

    /** 获取支付结果最大消费线程数 */
    public int getSettlementPaymentThreadMax() {
        return settlementPaymentThreadMax; // 返回支付结果 Consumer 线程上限
    }

    /** 设置支付结果最大消费线程数 */
    public void setSettlementPaymentThreadMax(int settlementPaymentThreadMax) {
        this.settlementPaymentThreadMax = settlementPaymentThreadMax; // 写入支付结果线程上限
    }

    /** 获取单条消息消费超时（分钟） */
    public int getConsumeTimeoutMinutes() {
        return consumeTimeoutMinutes; // 返回消费超时分钟数
    }

    /** 设置单条消息消费超时（分钟） */
    public void setConsumeTimeoutMinutes(int consumeTimeoutMinutes) {
        this.consumeTimeoutMinutes = consumeTimeoutMinutes; // 写入消费超时分钟数
    }

    /** 获取 Pull 批量大小 */
    public int getPullBatchSize() {
        return pullBatchSize; // 返回拉取批量
    }

    /** 设置 Pull 批量大小 */
    public void setPullBatchSize(int pullBatchSize) {
        this.pullBatchSize = pullBatchSize; // 写入拉取批量
    }

    /** 获取消费角色（拆分部署用） */
    public String getRoles() {
        return roles; // 返回当前实例启用的 Consumer 角色
    }

    /** 设置消费角色（拆分部署用） */
    public void setRoles(String roles) {
        this.roles = roles; // 写入 Consumer 角色过滤值
    }

    /** 判断当前实例是否应启用指定角色的 Consumer */
    public boolean isRoleEnabled(String role) {
        if (roles == null || roles.isBlank() || "all".equalsIgnoreCase(roles)) { // 未配置或为 all
            return true; // 启用全部 Consumer
        }
        for (String part : roles.split(",")) { // 逗号分隔多角色
            if (role.equalsIgnoreCase(part.trim())) { // 匹配目标角色
                return true; // 当前实例负责该角色
            }
        }
        return false; // 当前实例不负责该角色
    }
}
