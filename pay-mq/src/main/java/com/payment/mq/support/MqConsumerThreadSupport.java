package com.payment.mq.support;

import org.apache.rocketmq.client.consumer.DefaultMQPushConsumer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * RocketMQ 消费线程统一设置：同时写 min/max，避免默认 min=20 导致 YAML 降并发无效。
 */
public final class MqConsumerThreadSupport {

    private static final Logger log = LoggerFactory.getLogger(MqConsumerThreadSupport.class);

    private MqConsumerThreadSupport() {
    }

    /**
     * 统一设置消费线程 min/max，避免注解默认值导致 YAML 降并发无效。
     *
     * @param consumer RocketMQ PushConsumer
     * @param threads  目标线程数（至少 1）
     * @param role     角色标识，用于日志
     */
    public static void apply(DefaultMQPushConsumer consumer, int threads, String role) {
        int n = Math.max(1, threads);
        consumer.setConsumeThreadMin(n);
        consumer.setConsumeThreadMax(n);
        log.info("MQ consumer threads applied role={} min=max={}", role, n);
    }
}
