package com.payment.access.mq;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.payment.api.dto.TradeBillDTO;
import com.payment.api.service.BillAccessService;
import com.payment.common.enums.BillType;
import com.payment.mq.MqConsumerGroups;
import com.payment.mq.MqMessageHandler;
import com.payment.mq.MqTags;
import com.payment.mq.MqTopics;
import org.apache.rocketmq.spring.annotation.RocketMQMessageListener;
import org.apache.rocketmq.spring.core.RocketMQListener;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;

@Component
public class TradePayConsumer implements MqMessageHandler {

    private final BillAccessService billAccessService;
    private final ObjectMapper objectMapper;

    public TradePayConsumer(@Lazy BillAccessService billAccessService, ObjectMapper objectMapper) {
        this.billAccessService = billAccessService;
        this.objectMapper = objectMapper;
    }

    @Override
    public String topic() {
        return MqTopics.TRADE_PAY;
    }

    @Override
    public void handle(String payload) {
        try {
            TradeBillDTO bill = objectMapper.readValue(payload, TradeBillDTO.class);
            billAccessService.submitBill(bill);
        } catch (Exception e) {
            throw new IllegalStateException("trade pay consume failed", e);
        }
    }

    @Component
    @ConditionalOnProperty(name = "pay.mq.enabled", havingValue = "true")
    @RocketMQMessageListener(topic = MqTopics.TRADE_PAY, selectorExpression = MqTags.PAY, consumerGroup = MqConsumerGroups.ACCESS)
    public static class PayRocketListener implements RocketMQListener<String> {

        private final TradePayConsumer delegate;

        public PayRocketListener(TradePayConsumer delegate) {
            this.delegate = delegate;
        }

        @Override
        public void onMessage(String message) {
            delegate.handle(message);
        }
    }
}
