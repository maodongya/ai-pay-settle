package com.payment.fee.service;

import com.payment.common.redis.RedisDistributedLock;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Field;

import static org.junit.jupiter.api.Assertions.assertFalse;

class FeeCalcServiceImplNoLockTest {

    @Test
    void feeCalcService_hasNoDistributedLockField() {
        for (Field field : FeeCalcServiceImpl.class.getDeclaredFields()) {
            assertFalse(RedisDistributedLock.class.isAssignableFrom(field.getType()),
                    "unexpected lock field: " + field.getName());
        }
    }
}
