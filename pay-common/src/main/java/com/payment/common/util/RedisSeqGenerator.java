package com.payment.common.util;

import com.payment.common.cache.RedisKeys;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;

/**
 * 基于 Redis INCR 的单号生成器（多实例安全）。
 */
@Component
@ConditionalOnProperty(prefix = "payment.redis", name = "enabled", havingValue = "true", matchIfMissing = true)
public class RedisSeqGenerator implements BizSeqGenerator {

    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("yyyyMMdd");
    private static final Duration KEY_TTL = Duration.ofDays(2);

    private final StringRedisTemplate stringRedisTemplate;

    public RedisSeqGenerator(StringRedisTemplate stringRedisTemplate) {
        this.stringRedisTemplate = stringRedisTemplate;
    }

    @Override
    public String billNo() {
        return "CL" + LocalDate.now().format(DATE) + String.format("%06d", next(RedisKeys.seqBill(today())));
    }

    @Override
    public String settleNo() {
        return "ST" + LocalDate.now().format(DATE) + String.format("%08d", next(RedisKeys.seqSettle(today())));
    }

    @Override
    public String applyNo() {
        return "WD" + LocalDate.now().format(DATE) + String.format("%08d", next(RedisKeys.seqWithdraw(today())));
    }

    private String today() {
        return LocalDate.now().format(DATE);
    }

    private long next(String key) {
        Long seq = stringRedisTemplate.opsForValue().increment(key);
        if (seq == null) {
            throw new IllegalStateException("redis incr returned null for key=" + key);
        }
        if (seq == 1L) {
            stringRedisTemplate.expire(key, KEY_TTL);
        }
        return seq;
    }
}
