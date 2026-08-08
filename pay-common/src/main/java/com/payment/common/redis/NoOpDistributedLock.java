package com.payment.common.redis;

import com.payment.common.exception.BizException;
import com.payment.common.exception.ErrorCode;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.util.UUID;

/**
 * Redis 关闭时的锁实现：默认 fail-closed，禁止伪装加锁成功。
 */
@Component
@ConditionalOnProperty(prefix = "payment.redis", name = "enabled", havingValue = "false")
public class NoOpDistributedLock implements RedisDistributedLock {

    private final boolean failClosed;

    public NoOpDistributedLock(
            @Value("${payment.redis.lock.fail-closed:true}") boolean failClosed) {
        this.failClosed = failClosed;
    }

    @Override
    public String tryLock(String key, Duration ttl) {
        if (failClosed) {
            throw BizException.of(ErrorCode.REDIS_LOCK_UNAVAILABLE);
        }
        return UUID.randomUUID().toString();
    }

    @Override
    public void unlock(String key, String token) {
        // no-op
    }
}
