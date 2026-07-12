package com.payment.api.dto; // API 数据传输对象所在包

import java.math.BigDecimal; // 高精度金额类型

/**
 * 提现结果 DTO，封装提现申请处理结果
 */
public class WithdrawResultDTO {
    public String applyNo; // 提现申请号
    public String settleNo; // 结算单号
    public Integer status; // 提现状态
    public BigDecimal waitBalance; // 待结算余额
    public BigDecimal frozenBalance; // 冻结余额
}
