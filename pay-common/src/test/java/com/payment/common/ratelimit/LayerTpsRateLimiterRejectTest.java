package com.payment.common.ratelimit;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LayerTpsRateLimiterRejectTest {

    @Test
    void tryAcquire_secondImmediateCall_failsWhenTpsExhausted() {
        LayerTpsRateLimiter limiter = new LayerTpsRateLimiter(1);
        assertTrue(limiter.tryAcquire());
        assertFalse(limiter.tryAcquire());
    }
}
