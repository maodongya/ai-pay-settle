package com.payment.common.enums; // 任务状态枚举所在包

/**
 * 任务状态枚举，定义清分/结算等异步任务的生命周期状态
 */
public enum TaskStatus {
    PENDING(0), // 待执行
    RUNNING(1), // 执行中
    SUCCESS(2), // 执行成功
    FAILED(3), // 执行失败
    DEAD(4); // 死信（不可重试）

    private final int code; // 任务状态编码

    /**
     * 构造任务状态枚举
     *
     * @param code 状态编码
     */
    TaskStatus(int code) {
        this.code = code; // 保存状态编码
    }

    /**
     * 获取任务状态编码
     *
     * @return 状态编码
     */
    public int getCode() {
        return code; // 返回状态编码
    }

    /**
     * 根据编码解析任务状态
     *
     * @param code 状态编码
     * @return 对应的任务状态
     */
    public static TaskStatus of(int code) {
        for (TaskStatus s : values()) { // 遍历所有任务状态
            if (s.code == code) { // 匹配编码
                return s; // 返回匹配的状态
            }
        }
        throw new IllegalArgumentException("Unknown task status: " + code); // 未知编码时抛出异常
    }
}
