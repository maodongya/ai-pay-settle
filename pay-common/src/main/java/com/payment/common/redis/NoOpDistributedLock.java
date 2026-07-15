package com.payment.common.redis;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.UUID;

/**
 * Redis 关闭时的空实现：始终加锁成功。
 */
@Component
@ConditionalOnProperty(prefix = "payment.redis", name = "enabled", havingValue = "false")
public class NoOpDistributedLock implements RedisDistributedLock {

    @Override
    public String tryLock(String key, Duration ttl) {
        return UUID.randomUUID().toString();
    }

    @Override
    public void unlock(String key, String token) {
        // no-op
    }
}
