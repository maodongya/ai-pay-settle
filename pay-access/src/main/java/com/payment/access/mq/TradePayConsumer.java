package com.payment.access.mq; // 接入层 MQ Consumer 包

import com.fasterxml.jackson.databind.ObjectMapper; // JSON 反序列化
import com.payment.api.dto.TradeBillDTO; // 账单 DTO
import com.payment.api.service.BillAccessService; // 接入服务
import com.payment.mq.MqConsumerGroups; // Consumer Group
import com.payment.mq.MqMessageHandler; // Local 模式 Handler 接口
import com.payment.mq.MqTags; // PAY Tag
import com.payment.mq.MqTopics; // trade_pay Topic
import com.payment.mq.config.MqConsumerProperties;
import com.payment.mq.support.MqConsumerThreadSupport;
import com.payment.mq.support.MqListenerInvoker; // 统一 Listener 代理
import org.apache.rocketmq.client.consumer.DefaultMQPushConsumer;
import org.apache.rocketmq.spring.annotation.ConsumeMode; // 并发/有序模式
import org.apache.rocketmq.spring.annotation.RocketMQMessageListener; // Listener 注解
import org.apache.rocketmq.spring.core.RocketMQListener; // 回调接口
import org.apache.rocketmq.spring.core.RocketMQPushConsumerLifecycleListener;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty; // 条件装配
import org.springframework.context.annotation.Lazy; // 延迟注入破循环
import org.springframework.stereotype.Component; // 组件

/**
 * trade_pay_topic 消费者：落库并触发下游 clearance_task（接入层要快）。
 */
@Component // 注册 Handler Bean
public class TradePayConsumer implements MqMessageHandler {

    private final BillAccessService billAccessService; // 接入业务
    private final ObjectMapper objectMapper; // JSON

    /** 构造注入接入服务与 JSON 工具 */
    public TradePayConsumer(@Lazy BillAccessService billAccessService, ObjectMapper objectMapper) {
        this.billAccessService = billAccessService; // 接入服务
        this.objectMapper = objectMapper; // JSON 工具
    }

    /**
     * 返回本 Handler 监听的 Topic。
     */
    @Override // Handler 接口：Topic
    public String topic() {
        return MqTopics.TRADE_PAY; // Local 模式路由键
    }

    /**
     * 反序列化交易账单并提交接入层（落库+触发清算）。
     */
    @Override // Handler 接口：处理消息
    public void handle(String payload) {
        try { // 反序列化并提交
            TradeBillDTO bill = objectMapper.readValue(payload, TradeBillDTO.class); // JSON → DTO
            billAccessService.submitBill(bill); // 落库 + 触发清算 MQ
        } catch (Exception e) { // 包装为运行时异常供 Invoker 分类
            throw new IllegalStateException("trade pay consume failed", e); // 向上抛
        }
    }

    /**
     * RocketMQ 模式 Listener；线程数由 {@link MqConsumerProperties#getAccessThreadMax()} 在 prepareStart 生效。
     */
    @Component // 嵌套 Listener Bean
    @ConditionalOnProperty(name = "pay.mq.enabled", havingValue = "true") // 仅 RocketMQ
    @RocketMQMessageListener( // RocketMQ 注解
            topic = MqTopics.TRADE_PAY, // Topic
            selectorExpression = MqTags.PAY, // Tag 过滤
            consumerGroup = MqConsumerGroups.ACCESS, // Group
            consumeMode = ConsumeMode.CONCURRENTLY, // 并发消费
            consumeThreadMax = 20,
            consumeThreadNumber = 20)
    public static class PayRocketListener implements RocketMQListener<String>, RocketMQPushConsumerLifecycleListener {

        private final TradePayConsumer delegate; // 业务 Handler
        private final MqListenerInvoker invoker; // 异常分类 + 指标
        private final MqConsumerProperties consumerProperties;

        /** 构造注入 Handler、Invoker 与消费线程配置 */
        public PayRocketListener(TradePayConsumer delegate, MqListenerInvoker invoker,
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
            MqConsumerThreadSupport.apply(consumer, consumerProperties.getAccessThreadMax(), "access-pay");
        }

        /**
         * RocketMQ 消息回调，经 Invoker 统一异常分类与指标埋点。
         */
        @Override // 收到消息
        public void onMessage(String message) {
            invoker.invoke(MqTopics.TRADE_PAY, () -> delegate.handle(message)); // 统一 wrap
        }
    }
}
