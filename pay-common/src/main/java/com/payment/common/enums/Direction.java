package com.payment.common.enums; // 方向枚举所在包

/**
 * 方向枚举，定义应收/应付等账务方向
 */
public enum Direction {
    RECEIVABLE(1), // 应收
    PAYABLE(2); // 应付

    private final int code; // 方向编码

    /**
     * 构造方向枚举
     *
     * @param code 方向编码
     */
    Direction(int code) {
        this.code = code; // 保存方向编码
    }

    /**
     * 获取方向编码
     *
     * @return 方向编码
     */
    public int getCode() {
        return code; // 返回方向编码
    }
}
