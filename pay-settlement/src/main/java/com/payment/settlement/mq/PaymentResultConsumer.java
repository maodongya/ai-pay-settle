package com.payment.settlement.mq;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.payment.api.dto.PaymentCallbackDTO;
import com.payment.api.service.SettleAccountService;
import com.payment.mq.MqConsumerGroups;
import com.payment.mq.MqMessageHandler;
import com.payment.mq.MqTopics;
import org.apache.rocketmq.spring.annotation.RocketMQMessageListener;
import org.apache.rocketmq.spring.core.RocketMQListener;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;

@Component
public class PaymentResultConsumer implements MqMessageHandler {

    private final SettleAccountService settleAccountService;
    private final ObjectMapper objectMapper;

    public PaymentResultConsumer(@Lazy SettleAccountService settleAccountService, ObjectMapper objectMapper) {
        this.settleAccountService = settleAccountService;
        this.objectMapper = objectMapper;
    }

    @Override
    public String topic() {
        return MqTopics.PAYMENT_RESULT;
    }

    @Override
    public void handle(String payload) {
        try {
            PaymentCallbackDTO callback = objectMapper.readValue(payload, PaymentCallbackDTO.class);
            settleAccountService.handlePaymentCallback(callback);
        } catch (Exception e) {
            throw new IllegalStateException("payment result consume failed", e);
        }
    }

    @Component
    @ConditionalOnProperty(name = "pay.mq.enabled", havingValue = "true")
    @RocketMQMessageListener(topic = MqTopics.PAYMENT_RESULT, consumerGroup = MqConsumerGroups.SETTLEMENT + "-payment")
    public static class RocketListener implements RocketMQListener<String> {

        private final PaymentResultConsumer delegate;

        public RocketListener(PaymentResultConsumer delegate) {
            this.delegate = delegate;
        }

        @Override
        public void onMessage(String message) {
            delegate.handle(message);
        }
    }
}
