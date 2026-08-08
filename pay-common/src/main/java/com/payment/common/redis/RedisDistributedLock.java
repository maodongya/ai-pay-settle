package com.payment.common.redis;

import java.time.Duration;

/**
 * 分布式锁抽象。业务只通过本接口加解锁，不直接操作 RedisTemplate。
 */
public interface RedisDistributedLock {

    /**
     * 尝试加锁。
     *
     * @return 持有者 token；失败返回 null
     */
    String tryLock(String key, Duration ttl);

    /**
     * 释放锁（仅持有者可释放）。
     */
    void unlock(String key, String token);
}
