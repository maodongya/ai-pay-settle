package com.payment.common.ratelimit;

import java.util.concurrent.locks.LockSupport;

/**
 * 单层 TPS 限流器（全局串行令牌，阻塞 acquire）。
 */
final class LayerTpsRateLimiter {

    private final long intervalNanos;
    private long nextPermitNanos;

    LayerTpsRateLimiter(double tps) {
        double safeTps = tps > 0 ? tps : 1;
        this.intervalNanos = (long) (1_000_000_000L / safeTps);
        this.nextPermitNanos = System.nanoTime();
    }

    void acquire() {
        long waitNanos;
        synchronized (this) {
            long now = System.nanoTime();
            if (now < nextPermitNanos) {
                waitNanos = nextPermitNanos - now;
                nextPermitNanos += intervalNanos;
            } else {
                waitNanos = 0;
                nextPermitNanos = now + intervalNanos;
            }
        }
        if (waitNanos > 0) {
            LockSupport.parkNanos(waitNanos);
        }
    }
}
