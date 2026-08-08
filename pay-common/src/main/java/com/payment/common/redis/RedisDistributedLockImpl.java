package com.payment.common.redis;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.script.DefaultRedisScript;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.Duration;
import java.util.Collections;
import java.util.UUID;

/**
 * 基于 SET NX EX + Lua 释放的分布式锁。
 */
@Component
@ConditionalOnProperty(prefix = "payment.redis", name = "enabled", havingValue = "true", matchIfMissing = true)
public class RedisDistributedLockImpl implements RedisDistributedLock {

    private static final DefaultRedisScript<Long> UNLOCK_SCRIPT = new DefaultRedisScript<>();

    static {
        UNLOCK_SCRIPT.setResultType(Long.class);
        UNLOCK_SCRIPT.setScriptText(
                "if redis.call('get', KEYS[1]) == ARGV[1] then return redis.call('del', KEYS[1]) else return 0 end");
    }

    private final StringRedisTemplate stringRedisTemplate;

    public RedisDistributedLockImpl(StringRedisTemplate stringRedisTemplate) {
        this.stringRedisTemplate = stringRedisTemplate;
    }

    @Override
    public String tryLock(String key, Duration ttl) {
        if (TransactionSynchronizationManager.isActualTransactionActive()) {
            throw new IllegalStateException("redis lock forbidden inside DB transaction, key=" + key);
        }
        String token = UUID.randomUUID().toString();
        Boolean ok = stringRedisTemplate.opsForValue().setIfAbsent(key, token, ttl);
        return Boolean.TRUE.equals(ok) ? token : null;
    }

    @Override
    public void unlock(String key, String token) {
        if (key == null || token == null) {
            return;
        }
        stringRedisTemplate.execute(UNLOCK_SCRIPT, Collections.singletonList(key), token);
    }
}
