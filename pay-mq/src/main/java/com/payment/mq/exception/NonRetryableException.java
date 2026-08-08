package com.payment.mq.exception; // MQ 不可重试异常包

/**
 * 标记当前消息不应再触发 RocketMQ Client 重试的异常。
 * 由 {@link com.payment.mq.support.MqConsumeExceptionClassifier} 映射为 ACK 或 DLQ。
 */
public class NonRetryableException extends RuntimeException { // 继承运行时异常便于 Listener 抛出

    /**
     * 构造不可重试异常。
     *
     * @param message 说明原因的人类可读信息
     */
    public NonRetryableException(String message) {
        super(message); // 保存异常消息
    }

    /**
     * 构造不可重试异常（带根因）。
     *
     * @param message 说明原因的人类可读信息
     * @param cause   原始异常
     */
    public NonRetryableException(String message, Throwable cause) {
        super(message, cause); // 保存消息与根因
    }
}
