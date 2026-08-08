package com.payment.api.dto; // API 数据传输对象所在包

/**
 * 支付回调 DTO，封装支付渠道打款结果回调数据
 */
public class PaymentCallbackDTO {
    public String settleNo; // 结算单号
    public String channelTradeNo; // 渠道交易号
    public String status; // 回调状态
    public String failReason; // 失败原因
}
