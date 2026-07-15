package com.payment.common.redis;

import java.time.Duration;

/**
 * Redis 幂等短路（DB 唯一索引仍是正确性底线）。
 */
public interface RedisIdempotentGuard {

    /**
     * @return true 表示首次占用成功；false 表示已存在（重复请求）
     */
    boolean tryAcquire(String key, Duration ttl);
}
