package com.payment.settlement.channel;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.payment.api.dto.PaymentCallbackDTO;
import com.payment.api.service.SettleAccountService;
import com.payment.mq.MqTags;
import com.payment.mq.MqTopics;
import com.payment.mq.PayMqProducer;
import com.payment.mq.config.PayMqProperties;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Lazy;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.UUID;

@Component
public class MockPaymentChannel {

    private static final Logger log = LoggerFactory.getLogger(MockPaymentChannel.class);

    private final SettleAccountService settleAccountService;
    private final PayMqProducer payMqProducer;
    private final PayMqProperties payMqProperties;
    private final ObjectMapper objectMapper;

    public MockPaymentChannel(@Lazy SettleAccountService settleAccountService,
                              PayMqProducer payMqProducer,
                              PayMqProperties payMqProperties,
                              ObjectMapper objectMapper) {
        this.settleAccountService = settleAccountService;
        this.payMqProducer = payMqProducer;
        this.payMqProperties = payMqProperties;
        this.objectMapper = objectMapper;
    }

    @Async
    public void submitAsync(String settleNo, BigDecimal amount) {
        try {
            Thread.sleep(500);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        PaymentCallbackDTO callback = new PaymentCallbackDTO();
        callback.settleNo = settleNo;
        callback.channelTradeNo = "BK" + UUID.randomUUID().toString().substring(0, 12);
        callback.status = "SUCCESS";
        log.info("mock payment success settleNo={} amount={}", settleNo, amount);
        deliverCallback(callback);
    }

    private void deliverCallback(PaymentCallbackDTO callback) {
        if (payMqProperties.isPaymentCallbackViaMq()) {
            try {
                payMqProducer.sendOrderly(MqTopics.PAYMENT_RESULT, MqTags.CALLBACK, callback.settleNo, // 按 settleNo 有序
                        objectMapper.writeValueAsString(callback)); // JSON 载荷
            } catch (JsonProcessingException e) {
                throw new IllegalStateException(e);
            }
        } else {
            settleAccountService.handlePaymentCallback(callback);
        }
    }
}
