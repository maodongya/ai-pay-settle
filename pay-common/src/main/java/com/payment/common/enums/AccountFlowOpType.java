package com.payment.common.enums; // 账户流水操作类型枚举所在包

/**
 * 账户流水操作类型枚举，定义账户余额变动操作
 */
public enum AccountFlowOpType {
    CREDIT(1), // 入账
    FREEZE(2), // 冻结
    DEDUCT(3), // 扣减
    UNFREEZE(4), // 解冻
    REFUND_DEBIT(5), // 退款扣减
    ADJUST(6); // 调账

    private final int code; // 操作类型编码

    /**
     * 构造账户流水操作类型枚举
     *
     * @param code 操作编码
     */
    AccountFlowOpType(int code) {
        this.code = code; // 保存操作编码
    }

    /**
     * 获取操作类型编码
     *
     * @return 操作编码
     */
    public int getCode() {
        return code; // 返回操作编码
    }

    /**
     * 根据编码解析账户流水操作类型
     *
     * @param code 操作编码
     * @return 对应的操作类型
     */
    public static AccountFlowOpType of(int code) {
        for (AccountFlowOpType t : values()) { // 遍历所有操作类型
            if (t.code == code) { // 匹配编码
                return t; // 返回匹配的类型
            }
        }
        throw new IllegalArgumentException("Unknown account flow op: " + code); // 未知编码时抛出异常
    }
}
