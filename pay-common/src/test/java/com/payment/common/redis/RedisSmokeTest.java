package com.payment.common.redis;

import com.payment.common.cache.RedisKeys;
import com.payment.common.util.RedisSeqGenerator;
import org.junit.jupiter.api.Assumptions;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 对接本机 Redis 的冒烟测试（Redis 不可用时自动跳过）。
 */
class RedisSmokeTest {

    @Test
    void seqAndLockWorkAgainstLocalRedis() {
        LettuceConnectionFactory factory = new LettuceConnectionFactory("127.0.0.1", 6379);
        factory.afterPropertiesSet();
        try {
            factory.getConnection().ping();
        } catch (Exception ex) {
            Assumptions.assumeTrue(false, "local redis unavailable: " + ex.getMessage());
            return;
        }

        StringRedisTemplate template = new StringRedisTemplate(factory);
        template.afterPropertiesSet();

        RedisSeqGenerator seq = new RedisSeqGenerator(template);
        String settle1 = seq.settleNo();
        String settle2 = seq.settleNo();
        assertNotNull(settle1);
        assertNotEquals(settle1, settle2);
        assertTrue(settle1.startsWith("ST"));

        RedisDistributedLockImpl lock = new RedisDistributedLockImpl(template);
        String key = RedisKeys.settleLock(999001L);
        String token = lock.tryLock(key, Duration.ofSeconds(5));
        assertNotNull(token);
        assertEquals(null, lock.tryLock(key, Duration.ofSeconds(1)));
        lock.unlock(key, token);
        assertNotNull(lock.tryLock(key, Duration.ofSeconds(5)));

        factory.destroy();
    }
}
