package com.payment.mq;

/**
 * 统一 MQ 生产者接口，Local 与 RocketMQ 模式共用。
 */
public interface PayMqProducer {

    /** 发送普通消息（无 Tag/Keys） */
    void send(String topic, String payload);

    /** 发送带 Tag 的消息 */
    void send(String topic, String tag, String payload);

    /** 发送带 Tag 与 Keys 的消息（Keys 用于检索，不一定有序） */
    void send(String topic, String tag, String keys, String payload);

    /**
     * 按 hashKey 有序发送：同一 hashKey 路由到同一 Queue，配合 ORDERLY Consumer 保证顺序。
     *
     * @param topic   目标 Topic
     * @param tag     消息 Tag，可为 null
     * @param hashKey 路由键，通常为 merchantId 或 billNo
     * @param payload JSON 载荷
     */
    void sendOrderly(String topic, String tag, String hashKey, String payload);
}
