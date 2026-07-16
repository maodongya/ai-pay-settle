package com.payment.settlement.mq; // 结算 Consumer 包

import com.fasterxml.jackson.databind.JsonNode; // JSON
import com.fasterxml.jackson.databind.ObjectMapper; // 工具
import com.payment.api.service.SettleAccountService; // 入账服务
import com.payment.mq.MqConsumerGroups; // Group
import com.payment.mq.MqMessageHandler; // Handler
import com.payment.mq.MqTopics; // Topic
import com.payment.mq.config.MqConsumerProperties;
import com.payment.mq.support.MqConsumerThreadSupport;
import com.payment.mq.support.MqListenerInvoker; // Invoker
import org.apache.rocketmq.client.consumer.DefaultMQPushConsumer;
import org.apache.rocketmq.spring.annotation.ConsumeMode; // ORDERLY
import org.apache.rocketmq.spring.annotation.RocketMQMessageListener; // 注解
import org.apache.rocketmq.spring.core.RocketMQListener; // 接口
import org.apache.rocketmq.spring.core.RocketMQPushConsumerLifecycleListener;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty; // 条件
import org.springframework.context.annotation.Lazy; // 延迟
import org.springframework.stereotype.Component; // 组件

import java.math.BigDecimal; // 金额

/**
 * settle_amount_topic 消费者：同 merchant 有序入账，降低 30005 冲突。
 */
@Component // Handler
public class SettleAmountConsumer implements MqMessageHandler {

    private final SettleAccountService settleAccountService; // 账户服务
    private final ObjectMapper objectMapper; // JSON

    /** 构造注入结算服务与 JSON 工具 */
    public SettleAmountConsumer(@Lazy SettleAccountService settleAccountService, ObjectMapper objectMapper) {
        this.settleAccountService = settleAccountService; // 账户
        this.objectMapper = objectMapper; // JSON
    }

    /**
     * 返回本 Handler 监听的 Topic。
     */
    @Override // Topic
    public String topic() {
        return MqTopics.SETTLE_AMOUNT; // settle topic
    }

    /**
     * 解析 JSON 载荷，正向金额入账、负向金额退款扣减。
     */
    @Override // 处理入账/扣款
    public void handle(String payload) {
        try { // 解析载荷
            JsonNode node = objectMapper.readTree(payload); // JSON
            Long merchantId = node.get("merchantId").asLong(); // 商户
            String billNo = node.get("billNo").asText(); // 账单
            BigDecimal amount = new BigDecimal(node.get("amount").asText()); // 金额
            if (amount.signum() >= 0) { // 正向入账
                settleAccountService.creditBalance(merchantId, billNo, amount); // 贷记
            } else { // 退款扣减
                settleAccountService.debitRefundBalance(merchantId, billNo, amount.abs()); // 借记
            }
        } catch (Exception e) { // 失败
            throw new IllegalStateException("settle amount consume failed", e); // 抛出
        }
    }

    /** RocketMQ ORDERLY Listener */
    @Component // Listener
    @ConditionalOnProperty(name = "pay.mq.enabled", havingValue = "true") // MQ
    @RocketMQMessageListener( // 注解
            topic = MqTopics.SETTLE_AMOUNT, // Topic
            consumerGroup = MqConsumerGroups.SETTLEMENT, // settlement group
            consumeMode = ConsumeMode.ORDERLY, // 有序消费（配合 sendOrderly hashKey=merchantId）
            consumeThreadMax = 20,
            consumeThreadNumber = 20)
    public static class RocketListener implements RocketMQListener<String>, RocketMQPushConsumerLifecycleListener {

        private final SettleAmountConsumer delegate; // Handler
        private final MqListenerInvoker invoker; // Invoker
        private final MqConsumerProperties consumerProperties;

        /** 构造注入 Handler、Invoker 与消费线程配置 */
        public RocketListener(SettleAmountConsumer delegate, MqListenerInvoker invoker,
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
            MqConsumerThreadSupport.apply(consumer, consumerProperties.getSettlementThreadMax(), "settlement");
        }

        /**
         * RocketMQ 消息回调，经 Invoker 统一异常分类与指标埋点。
         */
        @Override // 回调
        public void onMessage(String message) {
            invoker.invoke(MqTopics.SETTLE_AMOUNT, () -> delegate.handle(message)); // wrap
        }
    }
}
