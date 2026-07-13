package com.payment.split.job; // 分账/Outbox 定时任务包

import com.payment.control.service.AlertService; // 告警
import com.payment.domain.entity.OutboxMessageEntity; // Outbox 实体
import com.payment.domain.repository.OutboxMessageRepository; // Outbox 仓储
import com.payment.domain.support.ShardScanSupport;
import com.payment.mq.MqTags; // CREDIT Tag
import com.payment.mq.PayMqProducer; // 生产者
import com.payment.mq.config.PayMqProperties; // MQ 开关
import com.payment.mq.support.PayMqProduceMetrics; // 投递指标
import org.slf4j.Logger; // 日志
import org.slf4j.LoggerFactory; // 日志工厂
import org.springframework.beans.factory.annotation.Value; // 注入 batch 配置
import org.springframework.scheduling.annotation.Scheduled; // 定时
import org.springframework.stereotype.Component; // 组件
import org.springframework.transaction.annotation.Transactional; // 事务

import java.util.ArrayList;
import java.util.List; // 列表

/**
 * 发件箱消息派发：经 MQ 有序投递 settle_amount_topic（同 merchant 串行入账）。
 * 按 16 分片并行扫描，避免全库广播。
 */
@Component // Spring 组件
public class OutboxDispatchJob {

    private static final Logger log = LoggerFactory.getLogger(OutboxDispatchJob.class); // 日志

    private final OutboxMessageRepository outboxMessageRepository; // Outbox 仓储
    private final PayMqProducer payMqProducer; // MQ 生产者
    private final PayMqProperties payMqProperties; // MQ 配置
    private final AlertService alertService; // 失败告警
    private final PayMqProduceMetrics produceMetrics; // Outbox 投递指标
    private final int batchSize; // 每批条数

    /** 构造注入 */
    public OutboxDispatchJob(OutboxMessageRepository outboxMessageRepository,
                             PayMqProducer payMqProducer,
                             PayMqProperties payMqProperties,
                             AlertService alertService,
                             PayMqProduceMetrics produceMetrics,
                             @Value("${pay.outbox.dispatch-batch-size:200}") int batchSize) {
        this.outboxMessageRepository = outboxMessageRepository; // 仓储
        this.payMqProducer = payMqProducer; // 生产者
        this.payMqProperties = payMqProperties; // 配置
        this.alertService = alertService; // 告警
        this.produceMetrics = produceMetrics; // 指标
        this.batchSize = batchSize; // 批量大小
    }

    /** 定时扫描 pending Outbox 并 sendOrderly */
    @Scheduled(fixedDelayString = "${pay.outbox.dispatch-interval-ms:5000}") // 默认 5s，mq profile 可改 1s
    @Transactional // 更新 status 与 DB 一致
    public void dispatch() {
        if (!payMqProperties.isOutboxViaMq()) { // 未走 MQ
            return; // 跳过
        }
        int perShard = ShardScanSupport.perShardLimit(batchSize);
        List<OutboxMessageEntity> pending = new ArrayList<>();
        ShardScanSupport.forEachShard(shardId -> pending.addAll(
                outboxMessageRepository.findTopNByStatusAndShardIdOrderByCreateTimeAsc(0, shardId, perShard)));
        int failCount = 0; // 本批失败计数
        for (OutboxMessageEntity msg : pending) { // 逐条发送
            try { // 发送
                String hashKey = msg.merchantId != null ? msg.merchantId.toString() : msg.bizKey;
                payMqProducer.sendOrderly(msg.topic, MqTags.CREDIT, hashKey, msg.payload); // 有序发送
                msg.status = 1; // 已发送
                outboxMessageRepository.save(msg); // 更新状态
                produceMetrics.recordOutboxDispatch(msg.topic, true);
            } catch (Exception e) { // 发送失败
                failCount++; // 失败 +1
                produceMetrics.recordOutboxDispatch(msg.topic, false);
                log.warn("outbox dispatch failed id={}", msg.id, e); // 警告日志
            }
        }
        if (failCount > 0) { // 存在失败
            alertService.send(AlertService.MQ_BACKLOG, AlertService.LEVEL_WARN, // 预警
                    "outbox dispatch batch failures=" + failCount); // 内容
        }
    }
}
