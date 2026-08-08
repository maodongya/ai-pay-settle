package com.payment.common.redis;

import com.payment.common.exception.BizException;
import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.junit.jupiter.api.Assertions.assertThrows;

class DistributedLockPolicyTest {

    @Test
    void noOp_failClosed_rejectsLock() {
        NoOpDistributedLock lock = new NoOpDistributedLock(true);
        assertThrows(BizException.class, () -> lock.tryLock("k", Duration.ofSeconds(1)));
    }
}
