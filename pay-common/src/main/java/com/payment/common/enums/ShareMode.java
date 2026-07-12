package com.payment.common.enums; // 分润模式枚举所在包

/**
 * 分润模式枚举，定义费用分润计算方式
 */
public enum ShareMode {
    FIXED_RATE(1), // 固定比例分润
    FIXED_AMOUNT(2), // 固定金额分润
    STEP_DOWN(3); // 阶梯递减分润

    private final int code; // 分润模式编码

    /**
     * 构造分润模式枚举
     *
     * @param code 模式编码
     */
    ShareMode(int code) {
        this.code = code; // 保存模式编码
    }

    /**
     * 获取分润模式编码
     *
     * @return 模式编码
     */
    public int getCode() {
        return code; // 返回模式编码
    }

    /**
     * 根据编码解析分润模式
     *
     * @param code 模式编码
     * @return 对应的分润模式
     */
    public static ShareMode of(int code) {
        for (ShareMode m : values()) { // 遍历所有分润模式
            if (m.code == code) { // 匹配编码
                return m; // 返回匹配的模式
            }
        }
        throw new IllegalArgumentException("Unknown share mode: " + code); // 未知编码时抛出异常
    }
}
