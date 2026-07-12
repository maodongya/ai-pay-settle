package com.payment.test.mq;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;

/**
 * trade_pay_topic 消息体，对应 docs/07 接口契约 §6.1。
 */
public class TradePayMessage {

    public String version = "1.0";
    public String billNo;
    public Integer billType = 1;
    public String businessLine = "A";
    public String category = "A01";
    public String serviceItem = "A0101";
    public Long merchantId;
    public Long agentId;
    public Long secondAgentId;
    public String orderNo;
    public BigDecimal tradeAmount;
    public String cityCode = "110000";
    public String payChannel = "ALI_PAY";
    public String payTime;

    public static TradePayMessage sample(MqLoadTestConfig config, int clientId, long seq) {
        TradePayMessage msg = new TradePayMessage();
        msg.billNo = "MQ" + clientId + "-" + seq;
        msg.merchantId = config.merchantId;
        msg.agentId = config.agentId;
        msg.secondAgentId = config.secondAgentId;
        msg.orderNo = "OD" + msg.billNo;
        msg.tradeAmount = config.tradeAmount;
        msg.payTime = OffsetDateTime.now(ZoneOffset.ofHours(8)).toString();
        return msg;
    }
}
