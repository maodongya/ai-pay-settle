package com.payment.common.ratelimit;

import java.util.EnumMap;
import java.util.Map;

/**
 * 按层维护 TPS 限流器实例。
 */
public class DbRateLimitRegistry {

    private final DbRateLimitProperties properties;
    private final Map<DbRateLimitLayer, LayerTpsRateLimiter> limiters = new EnumMap<>(DbRateLimitLayer.class);

    public DbRateLimitRegistry(DbRateLimitProperties properties) {
        this.properties = properties;
    }

    public void acquire(DbRateLimitLayer layer, double annotationTps) {
        if (!properties.isEnabled()) {
            return;
        }
        double tps = properties.resolveTps(layer, annotationTps);
        limiters.computeIfAbsent(layer, ignored -> new LayerTpsRateLimiter(tps)).acquire();
    }
}
