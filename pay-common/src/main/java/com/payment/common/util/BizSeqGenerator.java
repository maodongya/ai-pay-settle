package com.payment.common.util;

/**
 * 业务单号生成器接口（账单号 / 结算号 / 提现申请号）。
 */
public interface BizSeqGenerator {

    String billNo();

    String settleNo();

    String applyNo();
}
