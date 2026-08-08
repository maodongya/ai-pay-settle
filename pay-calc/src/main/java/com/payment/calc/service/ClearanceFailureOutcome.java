package com.payment.calc.service;

/**
 * 清算失败短事务结果：工单/告警由调用方在事务外处理。
 */
public record ClearanceFailureOutcome(
        String billNo, String errorMsg, boolean enteredDead, boolean updated) {
}
