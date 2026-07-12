package com.payment.settlement.mq;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.payment.api.service.SettleAccountService;
import com.payment.mq.MqConsumerGroups;
import com.payment.mq.MqMessageHandler;
import com.payment.mq.MqTopics;
import org.apache.rocketmq.spring.annotation.RocketMQMessageListener;
import org.apache.rocketmq.spring.core.RocketMQListener;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;

@Component
public class SettleAmountConsumer implements MqMessageHandler {

    private final SettleAccountService settleAccountService;
    private final ObjectMapper objectMapper;

    public SettleAmountConsumer(@Lazy SettleAccountService settleAccountService, ObjectMapper objectMapper) {
        this.settleAccountService = settleAccountService;
        this.objectMapper = objectMapper;
    }

    @Override
    public String topic() {
        return MqTopics.SETTLE_AMOUNT;
    }

    @Override
    public void handle(String payload) {
        try {
            JsonNode node = objectMapper.readTree(payload);
            Long merchantId = node.get("merchantId").asLong();
            String billNo = node.get("billNo").asText();
            BigDecimal amount = new BigDecimal(node.get("amount").asText());
            if (amount.signum() >= 0) {
                settleAccountService.creditBalance(merchantId, billNo, amount);
            } else {
                settleAccountService.debitRefundBalance(merchantId, billNo, amount.abs());
            }
        } catch (Exception e) {
            throw new IllegalStateException("settle amount consume failed", e);
        }
    }

    @Component
    @ConditionalOnProperty(name = "pay.mq.enabled", havingValue = "true")
    @RocketMQMessageListener(topic = MqTopics.SETTLE_AMOUNT, consumerGroup = MqConsumerGroups.SETTLEMENT)
    public static class RocketListener implements RocketMQListener<String> {

        private final SettleAmountConsumer delegate;

        public RocketListener(SettleAmountConsumer delegate) {
            this.delegate = delegate;
        }

        @Override
        public void onMessage(String message) {
            delegate.handle(message);
        }
    }
}
