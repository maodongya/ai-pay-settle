package com.payment.common.enums; // 账单状态枚举所在包

/**
 * 账单状态枚举，定义账单在清分流程中的处理状态
 */
public enum BillStatus {
    PENDING(0), // 待处理
    CLEARING(1), // 清分中
    CLEARED(2), // 已清分
    FAILED(3), // 清分失败
    WAIT_ORIGIN(4); // 等待原单

    private final int code; // 账单状态编码

    /**
     * 构造账单状态枚举
     *
     * @param code 状态编码
     */
    BillStatus(int code) {
        this.code = code; // 保存状态编码
    }

    /**
     * 获取账单状态编码
     *
     * @return 状态编码
     */
    public int getCode() {
        return code; // 返回状态编码
    }

    /**
     * 根据编码解析账单状态
     *
     * @param code 状态编码
     * @return 对应的账单状态
     */
    public static BillStatus of(int code) {
        for (BillStatus s : values()) { // 遍历所有账单状态
            if (s.code == code) { // 匹配编码
                return s; // 返回匹配的状态
            }
        }
        throw new IllegalArgumentException("Unknown bill status: " + code); // 未知编码时抛出异常
    }
}
