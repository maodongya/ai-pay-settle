package com.payment.common.redis;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.time.Duration;

/**
 * Redis 关闭时：始终视为首次，交由 DB 幂等兜底。
 */
@Component
@ConditionalOnProperty(prefix = "payment.redis", name = "enabled", havingValue = "false")
public class NoOpIdempotentGuard implements RedisIdempotentGuard {

    @Override
    public boolean tryAcquire(String key, Duration ttl) {
        return true;
    }
}
