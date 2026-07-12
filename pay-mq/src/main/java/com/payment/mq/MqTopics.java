package com.payment.mq;

public final class MqTopics {

    public static final String TRADE_PAY = "trade_pay_topic";
    public static final String TRADE_REFUND = "trade_refund_topic";
    public static final String CLEARANCE_TASK = "clearance_task_topic";
    public static final String SETTLE_AMOUNT = "settle_amount_topic";
    public static final String PAYMENT_RESULT = "payment_result_topic";

    private MqTopics() {
    }
}
