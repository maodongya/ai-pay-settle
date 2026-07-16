package com.payment.mq;

/**
 * RocketMQ Consumer Group 常量。
 */
public final class MqConsumerGroups {

    /** 接入层 trade_pay Consumer Group */
    public static final String ACCESS = "pay-access-consumer";
    /** 退款接入独立 Group，避免与 PAY 同组双订阅干扰位点与并行度 */
    public static final String ACCESS_REFUND = "pay-access-refund-consumer";
    /** 清算层 clearance_task Consumer Group */
    public static final String CALC = "pay-calc-consumer";
    /** 结算层 settle_amount Consumer Group */
    public static final String SETTLEMENT = "pay-settlement-consumer";
    /** 网关层 Consumer Group（预留） */
    public static final String GATEWAY = "pay-gateway-consumer";

    /**
     * 私有构造，禁止实例化。
     */
    private MqConsumerGroups() {
    }
}
