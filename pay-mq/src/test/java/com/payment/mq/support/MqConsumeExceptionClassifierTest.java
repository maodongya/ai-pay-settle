package com.payment.mq.support;

import com.payment.common.exception.BizException;
import com.payment.common.exception.ErrorCode;
import com.payment.mq.exception.NonRetryableException;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class MqConsumeExceptionClassifierTest {

    private final MqConsumeExceptionClassifier classifier = new MqConsumeExceptionClassifier();

    @Test
    void rateLimited_acks_toAvoidRetryAmplification() {
        assertEquals(MqConsumeExceptionClassifier.MqConsumeAction.ACK,
                classifier.classify(BizException.of(ErrorCode.RATE_LIMITED)));
    }

    @Test
    void nonRetryable_acks() {
        assertEquals(MqConsumeExceptionClassifier.MqConsumeAction.ACK,
                classifier.classify(new NonRetryableException("dead")));
    }

    @Test
    void concurrentUpdate_retries() {
        assertEquals(MqConsumeExceptionClassifier.MqConsumeAction.RETRY,
                classifier.classify(BizException.of(ErrorCode.CONCURRENT_UPDATE)));
    }
}
