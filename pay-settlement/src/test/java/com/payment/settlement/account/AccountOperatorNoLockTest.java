package com.payment.settlement.account;

import org.junit.jupiter.api.Test;
import org.springframework.transaction.annotation.Transactional;

import java.lang.reflect.Method;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AccountOperatorNoLockTest {

    @Test
    void publicMutators_areNotTransactional() {
        for (String name : new String[]{"credit", "freeze", "unfreeze", "deductFrozen", "debit"}) {
            boolean found = false;
            for (Method m : AccountOperator.class.getDeclaredMethods()) {
                if (!m.getName().equals(name)) {
                    continue;
                }
                found = true;
                assertFalse(m.isAnnotationPresent(Transactional.class), name);
            }
            assertTrue(found, "missing method " + name);
        }
    }
}
