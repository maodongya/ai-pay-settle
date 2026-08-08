package com.payment.common.p0;

import com.payment.common.exception.BizException;
import com.payment.common.exception.ErrorCode;
import com.payment.common.ratelimit.DbRateLimitLayer;
import com.payment.common.ratelimit.DbRateLimitProperties;
import com.payment.common.ratelimit.DbRateLimitRegistry;
import com.payment.common.redis.NoOpDistributedLock;
import com.payment.common.redis.RedisDistributedLockImpl;
import com.payment.common.tx.AfterCommit;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * P0-4 / AfterCommit 验收：fail-closed、事务内禁锁、reject 限流、回滚不触发 afterCommit。
 */
class CommonP0AcceptanceTest {

    @AfterEach
    void clearTx() {
        if (TransactionSynchronizationManager.isSynchronizationActive()) {
            TransactionSynchronizationManager.clearSynchronization();
        }
        TransactionSynchronizationManager.setActualTransactionActive(false);
    }

    @Test
    void afterCommit_skippedOnRollback_runsOnCommit() {
        AtomicBoolean ran = new AtomicBoolean(false);
        TransactionSynchronizationManager.initSynchronization();
        try {
            AfterCommit.run(() -> ran.set(true));
            for (TransactionSynchronization sync : TransactionSynchronizationManager.getSynchronizations()) {
                sync.afterCompletion(TransactionSynchronization.STATUS_ROLLED_BACK);
            }
            assertFalse(ran.get(), "rollback must not run afterCommit action");
        } finally {
            TransactionSynchronizationManager.clearSynchronization();
        }

        ran.set(false);
        TransactionSynchronizationManager.initSynchronization();
        try {
            AfterCommit.run(() -> ran.set(true));
            for (TransactionSynchronization sync : TransactionSynchronizationManager.getSynchronizations()) {
                sync.afterCommit();
            }
            assertTrue(ran.get());
        } finally {
            TransactionSynchronizationManager.clearSynchronization();
        }
    }

    @Test
    void noOpLock_failClosed_rejects() {
        NoOpDistributedLock lock = new NoOpDistributedLock(true);
        BizException ex = assertThrows(BizException.class, () -> lock.tryLock("k", Duration.ofSeconds(1)));
        assertEquals(ErrorCode.REDIS_LOCK_UNAVAILABLE.getCode(), ex.getCode());
    }

    @Test
    void redisLock_forbiddenInsideActiveTransaction() {
        @SuppressWarnings("unchecked")
        ValueOperations<String, String> ops = mock(ValueOperations.class);
        StringRedisTemplate template = mock(StringRedisTemplate.class);
        when(template.opsForValue()).thenReturn(ops);

        RedisDistributedLockImpl lock = new RedisDistributedLockImpl(template);
        TransactionSynchronizationManager.setActualTransactionActive(true);
        assertThrows(IllegalStateException.class, () -> lock.tryLock("settle:1", Duration.ofSeconds(1)));
        verify(ops, never()).setIfAbsent(anyString(), anyString(), any(Duration.class));
    }

    @Test
    void redisLock_allowedOutsideTransaction() {
        @SuppressWarnings("unchecked")
        ValueOperations<String, String> ops = mock(ValueOperations.class);
        StringRedisTemplate template = mock(StringRedisTemplate.class);
        when(template.opsForValue()).thenReturn(ops);
        when(ops.setIfAbsent(eq("k"), anyString(), any(Duration.class))).thenReturn(true);

        RedisDistributedLockImpl lock = new RedisDistributedLockImpl(template);
        TransactionSynchronizationManager.setActualTransactionActive(false);
        assertTrue(lock.tryLock("k", Duration.ofSeconds(1)) != null);
    }

    @Test
    void rateLimit_rejectMode_throwsRateLimited() {
        DbRateLimitProperties props = new DbRateLimitProperties();
        props.setEnabled(true);
        props.setMode("reject");
        props.setAccessTps(1);
        DbRateLimitRegistry registry = new DbRateLimitRegistry(props);

        registry.acquire(DbRateLimitLayer.ACCESS, 0);
        BizException ex = assertThrows(BizException.class,
                () -> registry.acquire(DbRateLimitLayer.ACCESS, 0));
        assertEquals(ErrorCode.RATE_LIMITED.getCode(), ex.getCode());
    }
}
