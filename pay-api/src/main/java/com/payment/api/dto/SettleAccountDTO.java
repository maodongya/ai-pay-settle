package com.payment.api.dto; // API 数据传输对象所在包

import java.math.BigDecimal; // 高精度金额类型

/**
 * 结算账户 DTO，封装商户账户余额信息
 */
public class SettleAccountDTO {
    public Long merchantId; // 商户 ID
    public BigDecimal waitBalance; // 待结算余额
    public BigDecimal frozenBalance; // 冻结余额
    public BigDecimal availableBalance; // 可用余额
    public Integer settleMode; // 结算模式
    public BigDecimal minWithdraw; // 最低提现金额
}
