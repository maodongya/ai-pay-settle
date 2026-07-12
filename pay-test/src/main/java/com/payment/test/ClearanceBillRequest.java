package com.payment.test;

import java.math.BigDecimal;

/**
 * 清算账单提交请求体，对应 ClearanceController.BillSubmitRequest。
 */
public class ClearanceBillRequest {

    public String billNo;
    public Integer billType;
    public String businessLine;
    public String category;
    public String serviceItem;
    public Long merchantId;
    public Long agentId;
    public Long secondAgentId;
    public String orderNo;
    public String originBillNo;
    public BigDecimal tradeAmount;
    public String cityCode;
    public String payChannel;

    public static ClearanceBillRequest sample(LoadTestConfig config, int clientId, long seq) {
        ClearanceBillRequest req = new ClearanceBillRequest();
        req.billNo = "LT" + clientId + "-" + seq;
        req.billType = 1;
        req.businessLine = "A";
        req.category = "A01";
        req.serviceItem = "A0101";
        req.merchantId = config.merchantId;
        req.agentId = config.agentId;
        req.secondAgentId = config.secondAgentId;
        req.orderNo = "OD" + req.billNo;
        req.tradeAmount = config.tradeAmount;
        req.cityCode = "110000";
        req.payChannel = "ALI_PAY";
        return req;
    }
}
