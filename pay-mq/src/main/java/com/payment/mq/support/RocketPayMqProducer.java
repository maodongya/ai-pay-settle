package com.payment.mq.support; // RocketMQ 生产者实现包

import com.payment.mq.PayMqProducer; // 统一生产者接口
import org.apache.rocketmq.client.producer.SendResult; // 发送结果
import org.apache.rocketmq.client.producer.SendStatus; // 发送状态枚举
import org.apache.rocketmq.spring.core.RocketMQTemplate; // Spring RocketMQ 模板
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty; // 条件 Bean
import org.springframework.messaging.support.MessageBuilder; // 构建 Spring Message
import org.springframework.stereotype.Component; // 注册为组件

/**
 * RocketMQ 模式下的同步生产者，支持普通发送与有序发送。
 */
@Component // 注入容器
@ConditionalOnProperty(name = "pay.mq.enabled", havingValue = "true") // pay.mq.enabled=true 时生效
public class RocketPayMqProducer implements PayMqProducer {

    private final RocketMQTemplate rocketMQTemplate; // RocketMQ 发送模板
    private final PayMqProduceMetrics produceMetrics; // 生产指标

    /**
     * 构造注入 RocketMQTemplate。
     */
    public RocketPayMqProducer(RocketMQTemplate rocketMQTemplate, PayMqProduceMetrics produceMetrics) {
        this.rocketMQTemplate = rocketMQTemplate; // 保存模板
        this.produceMetrics = produceMetrics; // 保存指标
    }

    @Override // 实现接口
    public void send(String topic, String payload) {
        send(topic, null, null, payload); // 委托四参数方法
    }

    @Override // 实现接口
    public void send(String topic, String tag, String payload) {
        send(topic, tag, null, payload); // 委托四参数方法
    }

    @Override // 实现接口
    public void send(String topic, String tag, String keys, String payload) {
        produceMetrics.record(topic, () -> doSend(topic, tag, keys, payload));
    }

    @Override // 实现接口
    public void sendOrderly(String topic, String tag, String hashKey, String payload) {
        produceMetrics.record(topic, () -> doSendOrderly(topic, tag, hashKey, payload));
    }

    private void doSend(String topic, String tag, String keys, String payload) {
        String destination = buildDestination(topic, tag); // topic 或 topic:tag
        SendResult result = rocketMQTemplate.syncSend(destination, MessageBuilder.withPayload(payload) // 构建消息体
                .setHeader("KEYS", keys != null ? keys : "") // 设置 KEYS 头便于 Console 检索
                .build()); // 同步发送
        assertSendOk(topic, result); // 校验 SEND_OK
    }

    private void doSendOrderly(String topic, String tag, String hashKey, String payload) {
        String destination = buildDestination(topic, tag); // topic 或 topic:tag
        SendResult result = rocketMQTemplate.syncSendOrderly(destination, MessageBuilder.withPayload(payload) // 有序消息体
                .setHeader("KEYS", hashKey != null ? hashKey : "") // KEYS 与 hashKey 一致
                .build(), hashKey); // hashKey 决定 Queue 选择
        assertSendOk(topic, result); // 校验 SEND_OK
    }

    /** 拼接 destination：无 tag 时仅 topic */
    private String buildDestination(String topic, String tag) {
        return tag == null || tag.isBlank() ? topic : topic + ":" + tag; // RocketMQ Spring 约定
    }

    /** 发送失败时抛异常，避免静默丢消息 */
    private void assertSendOk(String topic, SendResult result) {
        if (result.getSendStatus() != SendStatus.SEND_OK) { // 非成功状态
            throw new IllegalStateException("rocketmq send failed topic=" + topic + " status=" + result.getSendStatus()); // 快速失败
        }
    }
}
