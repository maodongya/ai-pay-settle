package com.payment.mq.support;

import com.payment.mq.PayMqProducer;
import org.apache.rocketmq.client.producer.SendResult;
import org.apache.rocketmq.client.producer.SendStatus;
import org.apache.rocketmq.spring.core.RocketMQTemplate;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.messaging.support.MessageBuilder;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "pay.mq.enabled", havingValue = "true")
public class RocketPayMqProducer implements PayMqProducer {

    private final RocketMQTemplate rocketMQTemplate;

    public RocketPayMqProducer(RocketMQTemplate rocketMQTemplate) {
        this.rocketMQTemplate = rocketMQTemplate;
    }

    @Override
    public void send(String topic, String payload) {
        send(topic, null, null, payload);
    }

    @Override
    public void send(String topic, String tag, String payload) {
        send(topic, tag, null, payload);
    }

    @Override
    public void send(String topic, String tag, String keys, String payload) {
        String destination = tag == null || tag.isBlank() ? topic : topic + ":" + tag;
        SendResult result = rocketMQTemplate.syncSend(destination, MessageBuilder.withPayload(payload)
                .setHeader("KEYS", keys != null ? keys : "")
                .build());
        if (result.getSendStatus() != SendStatus.SEND_OK) {
            throw new IllegalStateException("rocketmq send failed topic=" + topic + " status=" + result.getSendStatus());
        }
    }
}
