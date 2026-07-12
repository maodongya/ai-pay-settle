package com.payment.api.dto; // API 数据传输对象所在包

import java.math.BigDecimal; // 高精度金额类型

/**
 * 费用计算请求 DTO，封装分润费用计算入参
 */
public class FeeCalcDTO {
    public String billNo; // 账单号
    public Long merchantId; // 商户 ID
    public Long agentId; // 一级代理 ID
    public Long secondAgentId; // 二级代理 ID
    public Long splitPartyId; // 分润方 ID
    public BigDecimal tradeAmount; // 交易金额
    public String businessLine; // 业务线
    public String category; // 品类
    public String serviceItem; // 服务项目
    public String cityCode; // 城市编码
}
