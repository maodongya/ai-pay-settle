package com.payment.common.enums; // 结算单状态枚举所在包

/**
 * 结算单状态枚举，定义结算订单的处理状态
 */
public enum SettleOrderStatus {
    CREATED(0), // 已创建
    FROZEN(1), // 已冻结
    PAYING(2), // 打款中
    SUCCESS(3), // 打款成功
    FAILED(4), // 打款失败
    DISPUTE(5); // 争议中

    private final int code; // 结算单状态编码

    /**
     * 构造结算单状态枚举
     *
     * @param code 状态编码
     */
    SettleOrderStatus(int code) {
        this.code = code; // 保存状态编码
    }

    /**
     * 获取结算单状态编码
     *
     * @return 状态编码
     */
    public int getCode() {
        return code; // 返回状态编码
    }

    /**
     * 根据编码解析结算单状态
     *
     * @param code 状态编码
     * @return 对应的结算单状态
     */
    public static SettleOrderStatus of(int code) {
        for (SettleOrderStatus s : values()) { // 遍历所有结算单状态
            if (s.code == code) { // 匹配编码
                return s; // 返回匹配的状态
            }
        }
        throw new IllegalArgumentException("Unknown settle order status: " + code); // 未知编码时抛出异常
    }
}
