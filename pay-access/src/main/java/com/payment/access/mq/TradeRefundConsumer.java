package com.payment.access.mq; // 接入层退款 Consumer

import com.fasterxml.jackson.databind.ObjectMapper; // JSON
import com.payment.api.dto.TradeBillDTO; // DTO
import com.payment.api.service.BillAccessService; // 接入服务
import com.payment.common.enums.BillType; // 退款类型
import com.payment.mq.MqConsumerGroups; // Group
import com.payment.mq.MqMessageHandler; // Handler 接口
import com.payment.mq.MqTags; // REFUND Tag
import com.payment.mq.MqTopics; // Topic
import com.payment.mq.config.MqConsumerProperties;
import com.payment.mq.support.MqConsumerThreadSupport;
import com.payment.mq.support.MqListenerInvoker; // Invoker
import org.apache.rocketmq.client.consumer.DefaultMQPushConsumer;
import org.apache.rocketmq.spring.annotation.ConsumeMode; // 消费模式
import org.apache.rocketmq.spring.annotation.RocketMQMessageListener; // 注解
import org.apache.rocketmq.spring.core.RocketMQListener; // 接口
import org.apache.rocketmq.spring.core.RocketMQPushConsumerLifecycleListener;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty; // 条件
import org.springframework.context.annotation.Lazy; // 延迟注入
import org.springframework.stereotype.Component; // 组件

/**
 * trade_refund_topic 消费者。
 */
@Component // Handler Bean
public class TradeRefundConsumer implements MqMessageHandler {

    private final BillAccessService billAccessService; // 接入
    private final ObjectMapper objectMapper; // JSON

    /** 构造注入接入服务与 JSON 工具 */
    public TradeRefundConsumer(@Lazy BillAccessService billAccessService, ObjectMapper objectMapper) {
        this.billAccessService = billAccessService; // 接入
        this.objectMapper = objectMapper; // JSON
    }

    /**
     * 返回本 Handler 监听的 Topic。
     */
    @Override // Topic
    public String topic() {
        return MqTopics.TRADE_REFUND; // refund topic
    }

    /**
     * 反序列化退款账单，强制 billType=REFUND 后提交接入层。
     */
    @Override // 处理
    public void handle(String payload) {
        try { // 解析退款单
            TradeBillDTO bill = objectMapper.readValue(payload, TradeBillDTO.class); // JSON
            bill.billType = BillType.REFUND.getCode(); // 强制退款类型
            billAccessService.submitBill(bill); // 提交
        } catch (Exception e) { // 失败
            throw new IllegalStateException("trade refund consume failed", e); // 抛出
        }
    }

    /** RocketMQ Listener（独立 Group，避免与 PAY 同组双订阅） */
    @Component // Listener
    @ConditionalOnProperty(name = "pay.mq.enabled", havingValue = "true") // MQ 开
    @RocketMQMessageListener( // 注解
            topic = MqTopics.TRADE_REFUND, // Topic
            selectorExpression = MqTags.REFUND, // Tag
            consumerGroup = MqConsumerGroups.ACCESS_REFUND,
            consumeMode = ConsumeMode.CONCURRENTLY, // 并发
            consumeThreadMax = 20,
            consumeThreadNumber = 20)
    public static class RefundRocketListener implements RocketMQListener<String>, RocketMQPushConsumerLifecycleListener {

        private final TradeRefundConsumer delegate; // Handler
        private final MqListenerInvoker invoker; // Invoker
        private final MqConsumerProperties consumerProperties;

        /** 构造注入 Handler、Invoker 与消费线程配置 */
        public RefundRocketListener(TradeRefundConsumer delegate, MqListenerInvoker invoker,
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
            MqConsumerThreadSupport.apply(consumer, consumerProperties.getAccessThreadMax(), "access-refund");
        }

        /**
         * RocketMQ 消息回调，经 Invoker 统一异常分类与指标埋点。
         */
        @Override // 消息回调
        public void onMessage(String message) {
            invoker.invoke(MqTopics.TRADE_REFUND, () -> delegate.handle(message)); // wrap
        }
    }
}
