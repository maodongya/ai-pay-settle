package com.payment.test;

import java.util.concurrent.locks.LockSupport;

/**
 * 全局限流器，控制压测整体 TPS。
 */
public class TpsRateLimiter {

    private final long intervalNanos;

    private long nextPermitNanos;

    public TpsRateLimiter(int tps) {
        this.intervalNanos = 1_000_000_000L / tps;
        this.nextPermitNanos = System.nanoTime();
    }

    public void acquire() {
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
