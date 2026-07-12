package com.payment.mq;

/**
 * 统一 MQ 生产者接口，Local 与 RocketMQ 模式共用。
 */
public interface PayMqProducer {

    void send(String topic, String payload);

    void send(String topic, String tag, String payload);

    void send(String topic, String tag, String keys, String payload);
}
