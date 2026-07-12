package com.payment.common.enums; // 参与方类型枚举所在包

/**
 * 参与方类型枚举，定义交易或分润中的参与主体类型
 */
public enum PartyType {
    PLATFORM(1), // 平台
    AGENT_L1(2), // 一级代理
    AGENT_L2(3), // 二级代理
    PARTNER(4), // 合作方
    MERCHANT(5); // 商户

    private final int code; // 参与方类型编码

    /**
     * 构造参与方类型枚举
     *
     * @param code 类型编码
     */
    PartyType(int code) {
        this.code = code; // 保存类型编码
    }

    /**
     * 获取参与方类型编码
     *
     * @return 类型编码
     */
    public int getCode() {
        return code; // 返回类型编码
    }

    /**
     * 根据编码解析参与方类型
     *
     * @param code 类型编码
     * @return 对应的参与方类型
     */
    public static PartyType of(int code) {
        for (PartyType t : values()) { // 遍历所有参与方类型
            if (t.code == code) { // 匹配编码
                return t; // 返回匹配的类型
            }
        }
        throw new IllegalArgumentException("Unknown party type: " + code); // 未知编码时抛出异常
    }
}
