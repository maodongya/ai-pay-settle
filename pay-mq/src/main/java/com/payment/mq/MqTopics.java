package com.payment.mq;

/**
 * RocketMQ Topic 常量，与 init-rocketmq.sh 及 Consumer 保持一致。
 */
public final class MqTopics {

    /** 交易支付接入 Topic */
    public static final String TRADE_PAY = "trade_pay_topic";
    /** 交易退款接入 Topic */
    public static final String TRADE_REFUND = "trade_refund_topic";
    /** 清算任务 Topic */
    public static final String CLEARANCE_TASK = "clearance_task_topic";
    /** 结算入账 Topic */
    public static final String SETTLE_AMOUNT = "settle_amount_topic";
    /** 支付渠道回调 Topic */
    public static final String PAYMENT_RESULT = "payment_result_topic";

    /**
     * 私有构造，禁止实例化。
     */
    private MqTopics() {
    }
}
