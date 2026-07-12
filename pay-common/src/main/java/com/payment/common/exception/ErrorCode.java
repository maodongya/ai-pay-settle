package com.payment.common.exception; // 错误码枚举所在包

/**
 * 错误码枚举，定义系统统一的业务错误码与消息
 */
public enum ErrorCode {
    SUCCESS(0, "success"), // 成功
    DUPLICATE_BILL(10001, "duplicate bill"), // 重复账单
    INVALID_PARAM(10002, "invalid parameter"), // 参数无效
    MERCHANT_INVALID(10003, "merchant invalid or frozen"), // 商户无效或已冻结
    ORIGIN_BILL_NOT_FOUND(10004, "origin bill not found"), // 原单未找到
    RULE_NOT_MATCHED(20001, "fee rule not matched"), // 费率规则未匹配
    NEGATIVE_SHARE(20002, "negative share amount"), // 分润金额为负
    NEGATIVE_NET(20003, "negative net amount after deduction"), // 扣减后净额为负
    TASK_RUNNING(20004, "clearance task running"), // 清分任务执行中
    INSUFFICIENT_BALANCE(30001, "insufficient balance"), // 余额不足
    BELOW_MIN_WITHDRAW(30002, "below minimum withdraw amount"), // 低于最低提现金额
    DUPLICATE_WITHDRAW(30003, "duplicate withdraw apply"), // 重复提现申请
    CONCURRENT_UPDATE(30005, "concurrent account update conflict"), // 账户并发更新冲突
    PAYMENT_IN_PROGRESS(30015, "payment in progress"), // 打款进行中
    PAYMENT_CHANNEL_TIMEOUT(40001, "payment channel timeout"), // 支付渠道超时
    PAYMENT_FAILED(40002, "payment failed"); // 支付失败

    private final int code; // 错误码
    private final String message; // 错误消息

    /**
     * 构造错误码枚举
     *
     * @param code    错误码
     * @param message 错误消息
     */
    ErrorCode(int code, String message) {
        this.code = code; // 保存错误码
        this.message = message; // 保存错误消息
    }

    /**
     * 获取错误码
     *
     * @return 错误码
     */
    public int getCode() {
        return code; // 返回错误码
    }

    /**
     * 获取错误消息
     *
     * @return 错误消息
     */
    public String getMessage() {
        return message; // 返回错误消息
    }
}
