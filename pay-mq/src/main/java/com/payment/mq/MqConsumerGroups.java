package com.payment.mq;

/**
 * RocketMQ Consumer Group 常量。
 */
public final class MqConsumerGroups {

    public static final String ACCESS = "pay-access-consumer";
    /** 退款接入独立 Group，避免与 PAY 同组双订阅干扰位点与并行度 */
    public static final String ACCESS_REFUND = "pay-access-refund-consumer";
    public static final String CALC = "pay-calc-consumer";
    public static final String SETTLEMENT = "pay-settlement-consumer";
    public static final String GATEWAY = "pay-gateway-consumer";

    private MqConsumerGroups() {
    }
}
