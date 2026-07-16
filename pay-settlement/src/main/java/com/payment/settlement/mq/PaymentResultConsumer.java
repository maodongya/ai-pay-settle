package com.payment.settlement.mq; // 支付结果 Consumer

import com.fasterxml.jackson.databind.ObjectMapper; // JSON
import com.payment.api.dto.PaymentCallbackDTO; // 回调 DTO
import com.payment.api.service.SettleAccountService; // 结算服务
import com.payment.mq.MqConsumerGroups; // Group
import com.payment.mq.MqMessageHandler; // Handler
import com.payment.mq.MqTopics; // Topic
import com.payment.mq.config.MqConsumerProperties;
import com.payment.mq.support.MqConsumerThreadSupport;
import com.payment.mq.support.MqListenerInvoker; // Invoker
import org.apache.rocketmq.client.consumer.DefaultMQPushConsumer;
import org.apache.rocketmq.spring.annotation.ConsumeMode; // 模式
import org.apache.rocketmq.spring.annotation.RocketMQMessageListener; // 注解
import org.apache.rocketmq.spring.core.RocketMQListener; // 接口
import org.apache.rocketmq.spring.core.RocketMQPushConsumerLifecycleListener;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty; // 条件
import org.springframework.context.annotation.Lazy; // 延迟
import org.springframework.stereotype.Component; // 组件

/**
 * payment_result_topic 消费者：处理渠道打款回调。
 */
@Component // Handler
public class PaymentResultConsumer implements MqMessageHandler {

    private final SettleAccountService settleAccountService; // 结算
    private final ObjectMapper objectMapper; // JSON

    /** 构造注入结算服务与 JSON 工具 */
    public PaymentResultConsumer(@Lazy SettleAccountService settleAccountService, ObjectMapper objectMapper) {
        this.settleAccountService = settleAccountService; // 结算
        this.objectMapper = objectMapper; // JSON
    }

    /**
     * 返回本 Handler 监听的 Topic。
     */
    @Override // Topic
    public String topic() {
        return MqTopics.PAYMENT_RESULT; // payment result
    }

    /**
     * 反序列化支付回调 DTO 并交给结算服务处理。
     */
    @Override // 处理回调
    public void handle(String payload) {
        try { // 反序列化
            PaymentCallbackDTO callback = objectMapper.readValue(payload, PaymentCallbackDTO.class); // DTO
            settleAccountService.handlePaymentCallback(callback); // 更新结算单
        } catch (Exception e) { // 失败
            throw new IllegalStateException("payment result consume failed", e); // 抛出
        }
    }

    /** RocketMQ Listener */
    @Component // Listener
    @ConditionalOnProperty(name = "pay.mq.enabled", havingValue = "true") // MQ
    @RocketMQMessageListener( // 注解
            topic = MqTopics.PAYMENT_RESULT, // Topic
            consumerGroup = MqConsumerGroups.SETTLEMENT + "-payment", // 独立 Group
            consumeMode = ConsumeMode.CONCURRENTLY, // 并发
            consumeThreadMax = 20,
            consumeThreadNumber = 20)
    public static class RocketListener implements RocketMQListener<String>, RocketMQPushConsumerLifecycleListener {

        private final PaymentResultConsumer delegate; // Handler
        private final MqListenerInvoker invoker; // Invoker
        private final MqConsumerProperties consumerProperties;

        /** 构造注入 Handler、Invoker 与消费线程配置 */
        public RocketListener(PaymentResultConsumer delegate, MqListenerInvoker invoker,
                              MqConsumerProperties consumerProperties) {
            this.delegate = delegate; // Handler
            this.invoker = invoker; // Invoker
            this.consumerProperties = consumerProperties;
        }

        /**
         * 启动前应用消费线程数配置。
         */
        @Override
        public void prepareStart(DefaultMQPushConsumer consumer) {
            MqConsumerThreadSupport.apply(consumer, consumerProperties.getSettlementPaymentThreadMax(), "settlement-payment");
        }

        /**
         * RocketMQ 消息回调，经 Invoker 统一异常分类与指标埋点。
         */
        @Override // 回调
        public void onMessage(String message) {
            invoker.invoke(MqTopics.PAYMENT_RESULT, () -> delegate.handle(message)); // wrap
        }
    }
}
