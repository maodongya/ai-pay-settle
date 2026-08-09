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

/**
 * 模拟支付渠道：异步提交打款并在成功后回调结算服务或发送 MQ。
 */
@Component
public class MockPaymentChannel {

    private static final Logger log = LoggerFactory.getLogger(MockPaymentChannel.class);

    private final SettleAccountService settleAccountService;
    private final PayMqProducer payMqProducer;
    private final PayMqProperties payMqProperties;
    private final ObjectMapper objectMapper;

    /**
     * 构造注入结算服务、MQ 生产者与配置。
     */
    public MockPaymentChannel(@Lazy SettleAccountService settleAccountService,
                              PayMqProducer payMqProducer,
                              PayMqProperties payMqProperties,
                              ObjectMapper objectMapper) {
        this.settleAccountService = settleAccountService;
        this.payMqProducer = payMqProducer;
        this.payMqProperties = payMqProperties;
        this.objectMapper = objectMapper;
    }

    /**
     * 异步模拟渠道打款，延迟后构造 SUCCESS 回调并投递。
     */
    @Async
    public void submitAsync(String settleNo, BigDecimal amount) {
        try {
            Thread.sleep(500);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        PaymentCallbackDTO callback = buildSuccessCallback(settleNo);
        log.info("mock payment success settleNo={} amount={}", settleNo, amount);
        deliverCallback(callback);
    }

    /**
     * R2：查单。Mock 渠道对未知单也返回 SUCCESS（便于 PAYING 补偿收敛）。
     */
    public PaymentCallbackDTO queryStatus(String settleNo) {
        return buildSuccessCallback(settleNo);
    }

    private static PaymentCallbackDTO buildSuccessCallback(String settleNo) {
        PaymentCallbackDTO callback = new PaymentCallbackDTO();
        callback.settleNo = settleNo;
        callback.channelTradeNo = "BK" + UUID.randomUUID().toString().substring(0, 12);
        callback.status = "SUCCESS";
        return callback;
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
