package com.payment.calc.mq; // 算账层 Consumer

import com.fasterxml.jackson.databind.JsonNode; // JSON 节点
import com.fasterxml.jackson.databind.ObjectMapper; // JSON
import com.payment.api.service.ClearanceTaskService; // 清算执行
import com.payment.mq.MqConsumerGroups; // Group
import com.payment.mq.MqMessageHandler; // Handler
import com.payment.mq.MqTopics; // Topic
import com.payment.mq.config.MqConsumerProperties;
import com.payment.mq.exception.NonRetryableException;
import com.payment.mq.support.MqConsumerThreadSupport;
import com.payment.mq.support.MqListenerInvoker; // Invoker
import org.apache.rocketmq.client.consumer.DefaultMQPushConsumer;
import org.apache.rocketmq.spring.annotation.ConsumeMode; // 模式
import org.apache.rocketmq.spring.annotation.RocketMQMessageListener; // 注解
import org.apache.rocketmq.spring.core.RocketMQListener; // 接口
import org.apache.rocketmq.spring.core.RocketMQPushConsumerLifecycleListener;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty; // 条件
import org.springframework.stereotype.Component; // 组件

/**
 * clearance_task_topic 消费者：执行计费+分账。
 */
@Component // Handler
public class ClearanceTaskConsumer implements MqMessageHandler {

    private final ClearanceTaskService clearanceTaskService; // 清算服务
    private final ObjectMapper objectMapper; // JSON

    /** 构造 */
    public ClearanceTaskConsumer(ClearanceTaskService clearanceTaskService, ObjectMapper objectMapper) {
        this.clearanceTaskService = clearanceTaskService; // 清算
        this.objectMapper = objectMapper; // JSON
    }

    @Override // Topic
    public String topic() {
        return MqTopics.CLEARANCE_TASK; // clearance topic
    }

    @Override // 处理
    public void handle(String payload) {
        try { // 解析 billNo / merchantId
            JsonNode node = objectMapper.readTree(payload); // JSON
            String billNo = node.get("billNo").asText();
            Long merchantId = node.has("merchantId") ? node.get("merchantId").asLong() : null;
            clearanceTaskService.executeTask(billNo, merchantId); // 执行清算
        } catch (NonRetryableException e) {
            throw e;
        } catch (Exception e) { // 失败
            throw new IllegalStateException("clearance task consume failed", e); // 抛出
        }
    }

    /** RocketMQ Listener */
    @Component // Listener Bean
    @ConditionalOnProperty(name = "pay.mq.enabled", havingValue = "true") // MQ
    @RocketMQMessageListener( // 注解
            topic = MqTopics.CLEARANCE_TASK, // Topic
            consumerGroup = MqConsumerGroups.CALC, // calc group
            consumeMode = ConsumeMode.CONCURRENTLY, // 并发（同 merchant 不同 Queue 并行）
            consumeThreadMax = 20,
            consumeThreadNumber = 20,
            consumeTimeout = 15L) // 分钟，与 consume-timeout-minutes 同步
    public static class RocketListener implements RocketMQListener<String>, RocketMQPushConsumerLifecycleListener {

        private final ClearanceTaskConsumer delegate; // Handler
        private final MqListenerInvoker invoker; // Invoker
        private final MqConsumerProperties consumerProperties;

        /** 构造 */
        public RocketListener(ClearanceTaskConsumer delegate, MqListenerInvoker invoker,
                              MqConsumerProperties consumerProperties) {
            this.delegate = delegate; // Handler
            this.invoker = invoker; // Invoker
            this.consumerProperties = consumerProperties;
        }

        @Override
        public void prepareStart(DefaultMQPushConsumer consumer) {
            MqConsumerThreadSupport.apply(consumer, consumerProperties.getCalcThreadMax(), "calc");
        }

        @Override // 回调
        public void onMessage(String message) {
            invoker.invoke(MqTopics.CLEARANCE_TASK, () -> delegate.handle(message)); // wrap
        }
    }
}
