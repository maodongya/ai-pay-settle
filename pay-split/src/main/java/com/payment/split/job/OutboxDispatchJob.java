package com.payment.split.job;

import com.payment.control.service.AlertService;
import com.payment.domain.entity.OutboxMessageEntity;
import com.payment.domain.repository.OutboxMessageRepository;
import com.payment.domain.support.ShardScanSupport;
import com.payment.mq.MqTags;
import com.payment.mq.PayMqProducer;
import com.payment.mq.config.PayMqProperties;
import com.payment.mq.support.PayMqProduceMetrics;
import com.payment.split.support.OutboxDispatchTxSupport;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * 发件箱消息派发：分片扫描 pending，MQ 发送在事务外，短事务仅标记已发送。
 */
@Component
public class OutboxDispatchJob {

    private static final Logger log = LoggerFactory.getLogger(OutboxDispatchJob.class);

    private final OutboxMessageRepository outboxMessageRepository;
    private final OutboxDispatchTxSupport outboxDispatchTxSupport;
    private final PayMqProducer payMqProducer;
    private final PayMqProperties payMqProperties;
    private final AlertService alertService;
    private final PayMqProduceMetrics produceMetrics;
    private final int batchSize;

    /**
     * 构造注入 Outbox 仓储、事务支持、MQ 生产者与告警服务。
     */
    public OutboxDispatchJob(OutboxMessageRepository outboxMessageRepository,
                             OutboxDispatchTxSupport outboxDispatchTxSupport,
                             PayMqProducer payMqProducer,
                             PayMqProperties payMqProperties,
                             AlertService alertService,
                             PayMqProduceMetrics produceMetrics,
                             @Value("${pay.outbox.dispatch-batch-size:200}") int batchSize) {
        this.outboxMessageRepository = outboxMessageRepository;
        this.outboxDispatchTxSupport = outboxDispatchTxSupport;
        this.payMqProducer = payMqProducer;
        this.payMqProperties = payMqProperties;
        this.alertService = alertService;
        this.produceMetrics = produceMetrics;
        this.batchSize = batchSize;
    }

    /**
     * 定时扫描 pending Outbox，先发 MQ 再短事务标记已发送。
     */
    @Scheduled(fixedDelayString = "${pay.outbox.dispatch-interval-ms:5000}")
    public void dispatch() {
        if (!payMqProperties.isOutboxViaMq()) {
            return;
        }
        int perShard = ShardScanSupport.perShardLimit(batchSize);
        List<OutboxMessageEntity> collected = new ArrayList<>();
        ShardScanSupport.forEachShard(shardId -> collected.addAll(
                outboxMessageRepository.findTopNByStatusAndShardIdOrderByCreateTimeAsc(0, shardId, perShard)));
        List<OutboxMessageEntity> pending = collected.size() > batchSize
                ? collected.subList(0, batchSize) : collected;
        int failCount = 0;
        for (OutboxMessageEntity msg : pending) {
            try {
                String hashKey = msg.merchantId != null ? msg.merchantId.toString() : msg.bizKey;
                payMqProducer.sendOrderly(msg.topic, MqTags.CREDIT, hashKey, msg.payload);
                if (!outboxDispatchTxSupport.markSent(msg.id, msg.merchantId)) {
                    log.debug("outbox already sent id={} merchantId={}", msg.id, msg.merchantId);
                }
                produceMetrics.recordOutboxDispatch(msg.topic, true);
            } catch (Exception e) {
                failCount++;
                produceMetrics.recordOutboxDispatch(msg.topic, false);
                log.warn("outbox dispatch failed id={} merchantId={}", msg.id, msg.merchantId, e);
            }
        }
        if (failCount > 0) {
            alertService.send(AlertService.MQ_BACKLOG, AlertService.LEVEL_WARN,
                    "outbox dispatch batch failures=" + failCount);
        }
    }
}
