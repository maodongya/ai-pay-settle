package com.payment.common.ratelimit;

/**
 * 各业务层 DB 入口限流桶。
 */
public enum DbRateLimitLayer {
    /** 接入层 DB 入口限流桶 */
    ACCESS,
    /** 清算层 DB 入口限流桶 */
    CALC,
    /** 结算层 DB 入口限流桶 */
    SETTLEMENT
}
