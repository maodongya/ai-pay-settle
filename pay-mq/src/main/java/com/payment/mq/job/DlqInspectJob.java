package com.payment.mq.job; // MQ DLQ 巡检 Job 包

import com.payment.control.service.AlertService; // 告警服务
import com.payment.control.service.ExceptionRecordService; // 异常工单服务
import com.payment.mq.MqConsumerGroups; // Consumer Group 常量
import com.payment.mq.config.PayMqProperties; // MQ 开关
import org.slf4j.Logger; // 日志
import org.slf4j.LoggerFactory; // 日志工厂
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty; // 条件装配
import org.springframework.scheduling.annotation.Scheduled; // 定时任务
import org.springframework.stereotype.Component; // Spring 组件

/**
 * DLQ 巡检 Job：周期性检查各 Consumer Group 死信队列是否非空并告警。
 * <p>
 * 完整实现需对接 RocketMQ Admin API（DefaultMQAdminExt）；当前版本在 MQ 开启时
 * 记录巡检日志并预留告警钩子，生产环境可替换 queryDlqCount 为真实 Admin 查询。
 */
@Component // 注册为 Spring Bean
@ConditionalOnProperty(name = "pay.mq.enabled", havingValue = "true") // 仅 RocketMQ 模式
public class DlqInspectJob {

    private static final Logger log = LoggerFactory.getLogger(DlqInspectJob.class); // 日志记录器

    public static final String ALERT_DLQ = "MQ_DLQ"; // DLQ 非空告警类型
    public static final String EX_DLQ = "EX-0109"; // DLQ 异常工单编码

    /** 需要巡检的 Consumer Group 列表（与 init-rocketmq.sh / Listener 一致） */
    private static final String[] CONSUMER_GROUPS = { // 各业务 Consumer Group
            MqConsumerGroups.ACCESS, // 接入层 trade_pay / trade_refund
            MqConsumerGroups.CALC, // 算账层 clearance_task
            MqConsumerGroups.SETTLEMENT, // 结算层 settle_amount
            MqConsumerGroups.SETTLEMENT + "-payment" // 支付结果 payment_result
    };

    private final PayMqProperties payMqProperties; // MQ 配置（含 nameServer）
    private final AlertService alertService; // 写 alert_record
    private final ExceptionRecordService exceptionRecordService; // 可选建 EX-0109 工单

    /**
     * 构造注入依赖。
     */
    public DlqInspectJob(PayMqProperties payMqProperties,
                         AlertService alertService,
                         ExceptionRecordService exceptionRecordService) {
        this.payMqProperties = payMqProperties; // 保存 MQ 配置
        this.alertService = alertService; // 保存告警服务
        this.exceptionRecordService = exceptionRecordService; // 保存工单服务
    }

    /**
     * 每 10 分钟巡检 DLQ（cron 可配置）。
     */
    @Scheduled(cron = "${pay.mq.dlq.inspect-cron:0 */10 * * * ?}") // 默认每 10 分钟
    public void inspectDlq() {
        if (!payMqProperties.isEnabled()) { // MQ 未开
            return; // 跳过
        }
        for (String group : CONSUMER_GROUPS) { // 逐个 Group 检查
            long dlqCount = queryDlqCount(group); // 查询 DLQ 消息数（可替换为 Admin API）
            if (dlqCount > 0) { // DLQ 非空
                String content = "DLQ non-empty group=" + group + " count=" + dlqCount; // 告警正文
                log.error(content); // 错误日志
                alertService.send(ALERT_DLQ, AlertService.LEVEL_ERROR, content); // P1 告警
                exceptionRecordService.openIfAbsent(EX_DLQ, "MQ", group, content); // 建工单（幂等）
            } else { // DLQ 为空
                log.debug("dlq inspect ok group={}", group); // 正常日志
            }
        }
    }

    /**
     * 查询指定 Consumer Group 的 DLQ 消息数。
     * <p>
     * 占位实现：返回 0。生产环境请使用 DefaultMQAdminExt 查询 %DLQ%{group} Topic 堆积。
     */
    private long queryDlqCount(String consumerGroup) {
        log.trace("dlq query placeholder group={} nameServer={}", consumerGroup, payMqProperties.getNameServer()); // 占位 trace
        return 0L; // 占位：无 Admin 客户端时视为 0
    }
}
