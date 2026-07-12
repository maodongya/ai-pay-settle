package com.payment.common.enums; // 账单类型枚举所在包

/**
 * 账单类型枚举，定义各类交易账单的业务分类
 */
public enum BillType {
    PAY(1), // 支付账单
    REFUND(2), // 退款账单
    RECHARGE(3), // 充值账单
    PROFIT_SHARE(4), // 分润账单
    REWARD_PUNISH(5), // 奖惩账单
    AUTH(6), // 授权账单
    PAYOUT(7), // 出款账单
    ORDER_CLEAR(8); // 订单清分账单

    private final int code; // 账单类型编码

    /**
     * 构造账单类型枚举
     *
     * @param code 类型编码
     */
    BillType(int code) {
        this.code = code; // 保存类型编码
    }

    /**
     * 获取账单类型编码
     *
     * @return 类型编码
     */
    public int getCode() {
        return code; // 返回类型编码
    }

    /**
     * 根据编码解析账单类型
     *
     * @param code 类型编码
     * @return 对应的账单类型
     */
    public static BillType of(int code) {
        for (BillType t : values()) { // 遍历所有账单类型
            if (t.code == code) { // 匹配编码
                return t; // 返回匹配的类型
            }
        }
        throw new IllegalArgumentException("Unknown bill type: " + code); // 未知编码时抛出异常
    }
}
