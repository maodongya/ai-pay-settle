package com.payment.common.ratelimit;

/**
 * 各业务层 DB 入口限流桶。
 */
public enum DbRateLimitLayer {
    ACCESS,
    CALC,
    SETTLEMENT
}
