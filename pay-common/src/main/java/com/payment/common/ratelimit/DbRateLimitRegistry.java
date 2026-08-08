package com.payment.common.ratelimit;

import com.payment.common.exception.BizException;
import com.payment.common.exception.ErrorCode;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.EnumMap;
import java.util.Map;

/**
 * 按层维护 TPS 限流器实例。
 */
public class DbRateLimitRegistry {

    private static final Logger log = LoggerFactory.getLogger(DbRateLimitRegistry.class);

    private final DbRateLimitProperties properties;
    private final Map<DbRateLimitLayer, LayerTpsRateLimiter> limiters = new EnumMap<>(DbRateLimitLayer.class);

    public DbRateLimitRegistry(DbRateLimitProperties properties) {
        this.properties = properties;
        log.info("db-rate-limit enabled={} mode={} accessTps={} calcTps={} settlementTps={} (clusterApprox=tps*instances)",
                properties.isEnabled(), properties.getMode(),
                properties.getAccessTps(), properties.getCalcTps(), properties.getSettlementTps());
    }

    public void acquire(DbRateLimitLayer layer, double annotationTps) {
        if (!properties.isEnabled()) {
            return;
        }
        double tps = properties.resolveTps(layer, annotationTps);
        LayerTpsRateLimiter limiter = limiters.computeIfAbsent(layer, ignored -> new LayerTpsRateLimiter(tps));
        if ("reject".equalsIgnoreCase(properties.getMode())) {
            if (!limiter.tryAcquire()) {
                throw BizException.of(ErrorCode.RATE_LIMITED);
            }
            return;
        }
        limiter.acquire();
    }
}
