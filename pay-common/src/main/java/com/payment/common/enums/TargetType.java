package com.payment.common.enums; // 目标类型枚举所在包

/**
 * 目标类型枚举，定义费用规则或分润的目标主体类型
 */
public enum TargetType {
    PLATFORM(1), // 平台
    AGENT_L1(2), // 一级代理
    AGENT_L2(3), // 二级代理
    PARTNER(4), // 合作方
    MERCHANT(5); // 商户

    private final int code; // 目标类型编码

    /**
     * 构造目标类型枚举
     *
     * @param code 类型编码
     */
    TargetType(int code) {
        this.code = code; // 保存类型编码
    }

    /**
     * 获取目标类型编码
     *
     * @return 类型编码
     */
    public int getCode() {
        return code; // 返回类型编码
    }

    /**
     * 根据编码解析目标类型
     *
     * @param code 类型编码
     * @return 对应的目标类型
     */
    public static TargetType of(int code) {
        for (TargetType t : values()) { // 遍历所有目标类型
            if (t.code == code) { // 匹配编码
                return t; // 返回匹配的类型
            }
        }
        throw new IllegalArgumentException("Unknown target type: " + code); // 未知编码时抛出异常
    }
}
