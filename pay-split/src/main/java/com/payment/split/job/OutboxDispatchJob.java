package com.payment.split.job;

import com.payment.domain.entity.OutboxMessageEntity;
import com.payment.domain.repository.OutboxMessageRepository;
import com.payment.mq.MqTags;
import com.payment.mq.PayMqProducer;
import com.payment.mq.config.PayMqProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/**
 * 发件箱消息派发：经 MQ 投递 settle_amount_topic（Local 模式同步消费）。
 */
@Component
public class OutboxDispatchJob {

    private static final Logger log = LoggerFactory.getLogger(OutboxDispatchJob.class);

    private final OutboxMessageRepository outboxMessageRepository;
    private final PayMqProducer payMqProducer;
    private final PayMqProperties payMqProperties;

    public OutboxDispatchJob(OutboxMessageRepository outboxMessageRepository,
                               PayMqProducer payMqProducer,
                               PayMqProperties payMqProperties) {
        this.outboxMessageRepository = outboxMessageRepository;
        this.payMqProducer = payMqProducer;
        this.payMqProperties = payMqProperties;
    }

    @Scheduled(fixedDelayString = "${pay.outbox.dispatch-interval-ms:5000}")
    @Transactional
    public void dispatch() {
        if (!payMqProperties.isOutboxViaMq()) {
            return;
        }
        List<OutboxMessageEntity> pending = outboxMessageRepository.findTop100ByStatusOrderByCreateTimeAsc(0);
        for (OutboxMessageEntity msg : pending) {
            try {
                payMqProducer.send(msg.topic, MqTags.CREDIT, msg.bizKey, msg.payload);
                msg.status = 1;
                outboxMessageRepository.save(msg);
            } catch (Exception e) {
                log.warn("outbox dispatch failed id={}", msg.id, e);
            }
        }
    }
}
