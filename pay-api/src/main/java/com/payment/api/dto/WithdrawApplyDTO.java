package com.payment.api.dto; // API 数据传输对象所在包

import java.math.BigDecimal; // 高精度金额类型

/**
 * 提现申请 DTO，封装商户提现请求参数
 */
public class WithdrawApplyDTO {
    public Long merchantId; // 商户 ID
    public BigDecimal withdrawAmount; // 提现金额
    public String settleCardNo; // 结算银行卡号
}
