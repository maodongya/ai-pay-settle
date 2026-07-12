package com.payment.api.dto; // API 数据传输对象所在包

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.math.BigDecimal; // 高精度金额类型

/**
 * 交易账单 DTO，封装交易账单接入数据
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public class TradeBillDTO {
    public String version; // 消息版本
    public String billNo; // 账单号
    public Integer billType; // 账单类型
    public String businessLine; // 业务线
    public String category; // 品类
    public String serviceItem; // 服务项目
    public Long merchantId; // 商户 ID
    public Long agentId; // 一级代理 ID
    public Long secondAgentId; // 二级代理 ID
    public String orderNo; // 订单号
    public String originBillNo; // 原账单号（退款等场景）
    public BigDecimal tradeAmount; // 交易金额
    public String cityCode; // 城市编码
    public String payChannel; // 支付渠道
    public String payTime; // 支付时间
}
