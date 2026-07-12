package com.payment.mq;

/**
 * RocketMQ Consumer Group 常量。
 */
public final class MqConsumerGroups {

    public static final String ACCESS = "pay-access-consumer";
    public static final String CALC = "pay-calc-consumer";
    public static final String SETTLEMENT = "pay-settlement-consumer";
    public static final String GATEWAY = "pay-gateway-consumer";

    private MqConsumerGroups() {
    }
}
