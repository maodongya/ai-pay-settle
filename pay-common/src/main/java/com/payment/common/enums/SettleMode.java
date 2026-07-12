package com.payment.common.enums; // 结算模式枚举所在包

/**
 * 结算模式枚举，定义商户资金结算周期
 */
public enum SettleMode {
    T1(1), // T+1 结算
    D0(2), // 当日结算
    D1(3), // 次日结算
    H0(4), // 小时级结算
    S0(5); // 实时结算

    private final int code; // 结算模式编码

    /**
     * 构造结算模式枚举
     *
     * @param code 模式编码
     */
    SettleMode(int code) {
        this.code = code; // 保存模式编码
    }

    /**
     * 获取结算模式编码
     *
     * @return 模式编码
     */
    public int getCode() {
        return code; // 返回模式编码
    }

    /**
     * 根据编码解析结算模式
     *
     * @param code 模式编码
     * @return 对应的结算模式
     */
    public static SettleMode of(int code) {
        for (SettleMode m : values()) { // 遍历所有结算模式
            if (m.code == code) { // 匹配编码
                return m; // 返回匹配的模式
            }
        }
        throw new IllegalArgumentException("Unknown settle mode: " + code); // 未知编码时抛出异常
    }
}
