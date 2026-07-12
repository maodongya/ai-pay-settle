package com.payment.calc.mq;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.payment.api.service.ClearanceTaskService;
import com.payment.mq.MqConsumerGroups;
import com.payment.mq.MqMessageHandler;
import com.payment.mq.MqTopics;
import org.apache.rocketmq.spring.annotation.RocketMQMessageListener;
import org.apache.rocketmq.spring.core.RocketMQListener;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
public class ClearanceTaskConsumer implements MqMessageHandler {

    private final ClearanceTaskService clearanceTaskService;
    private final ObjectMapper objectMapper;

    public ClearanceTaskConsumer(ClearanceTaskService clearanceTaskService, ObjectMapper objectMapper) {
        this.clearanceTaskService = clearanceTaskService;
        this.objectMapper = objectMapper;
    }

    @Override
    public String topic() {
        return MqTopics.CLEARANCE_TASK;
    }

    @Override
    public void handle(String payload) {
        try {
            JsonNode node = objectMapper.readTree(payload);
            clearanceTaskService.executeTask(node.get("billNo").asText());
        } catch (Exception e) {
            throw new IllegalStateException("clearance task consume failed", e);
        }
    }

    @Component
    @ConditionalOnProperty(name = "pay.mq.enabled", havingValue = "true")
    @RocketMQMessageListener(topic = MqTopics.CLEARANCE_TASK, consumerGroup = MqConsumerGroups.CALC)
    public static class RocketListener implements RocketMQListener<String> {

        private final ClearanceTaskConsumer delegate;

        public RocketListener(ClearanceTaskConsumer delegate) {
            this.delegate = delegate;
        }

        @Override
        public void onMessage(String message) {
            delegate.handle(message);
        }
    }
}
