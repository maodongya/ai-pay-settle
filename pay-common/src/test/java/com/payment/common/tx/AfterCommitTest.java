package com.payment.common.tx;

import org.junit.jupiter.api.Test;

import java.util.concurrent.atomic.AtomicBoolean;

import static org.junit.jupiter.api.Assertions.assertTrue;

class AfterCommitTest {

    @Test
    void runsImmediately_whenNoTransactionActive() {
        AtomicBoolean ran = new AtomicBoolean(false);
        AfterCommit.run(() -> ran.set(true));
        assertTrue(ran.get());
    }
}
