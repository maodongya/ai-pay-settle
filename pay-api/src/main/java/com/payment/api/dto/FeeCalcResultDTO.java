package com.payment.api.dto; // API 数据传输对象所在包

import java.math.BigDecimal; // 高精度金额类型

/**
 * 费用计算结果 DTO，封装各方分润明细
 */
public class FeeCalcResultDTO {
    public String billNo; // 账单号
    public Long merchantId; // 商户 ID
    public BigDecimal tradeAmount; // 交易金额
    public BigDecimal platformFee; // 平台费用
    public BigDecimal agentL1Share; // 一级代理分润
    public BigDecimal agentL2Share; // 二级代理分润
    public BigDecimal partnerShare; // 合作方分润
    public BigDecimal merchantIncome; // 商户收入
    public String ruleSnapshotJson; // 规则快照 JSON
}
