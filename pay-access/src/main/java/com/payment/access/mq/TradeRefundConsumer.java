package com.payment.access.mq; // 接入层退款 Consumer

import com.fasterxml.jackson.databind.ObjectMapper; // JSON
import com.payment.api.dto.TradeBillDTO; // DTO
import com.payment.api.service.BillAccessService; // 接入服务
import com.payment.common.enums.BillType; // 退款类型
import com.payment.mq.MqConsumerGroups; // Group
import com.payment.mq.MqMessageHandler; // Handler 接口
import com.payment.mq.MqTags; // REFUND Tag
import com.payment.mq.MqTopics; // Topic
import com.payment.mq.support.MqListenerInvoker; // Invoker
import org.apache.rocketmq.spring.annotation.ConsumeMode; // 消费模式
import org.apache.rocketmq.spring.annotation.RocketMQMessageListener; // 注解
import org.apache.rocketmq.spring.core.RocketMQListener; // 接口
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

    /** 构造 */
    public TradeRefundConsumer(@Lazy BillAccessService billAccessService, ObjectMapper objectMapper) {
        this.billAccessService = billAccessService; // 接入
        this.objectMapper = objectMapper; // JSON
    }

    @Override // Topic
    public String topic() {
        return MqTopics.TRADE_REFUND; // refund topic
    }

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

    /** RocketMQ Listener */
    @Component // Listener
    @ConditionalOnProperty(name = "pay.mq.enabled", havingValue = "true") // MQ 开
    @RocketMQMessageListener( // 注解
            topic = MqTopics.TRADE_REFUND, // Topic
            selectorExpression = MqTags.REFUND, // Tag
            consumerGroup = MqConsumerGroups.ACCESS, // 与 PAY 同 Group
            consumeMode = ConsumeMode.CONCURRENTLY, // 并发
            consumeThreadMax = 32) // 线程（与 access-thread-max 同步）
    public static class RefundRocketListener implements RocketMQListener<String> {

        private final TradeRefundConsumer delegate; // Handler
        private final MqListenerInvoker invoker; // Invoker

        /** 构造 */
        public RefundRocketListener(TradeRefundConsumer delegate, MqListenerInvoker invoker) {
            this.delegate = delegate; // Handler
            this.invoker = invoker; // Invoker
        }

        @Override // 消息回调
        public void onMessage(String message) {
            invoker.invoke(MqTopics.TRADE_REFUND, () -> delegate.handle(message)); // wrap
        }
    }
}
