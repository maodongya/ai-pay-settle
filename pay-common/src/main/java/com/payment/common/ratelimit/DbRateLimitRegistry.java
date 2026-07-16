package com.payment.common.ratelimit;

import java.util.EnumMap;
import java.util.Map;

/**
 * 按层维护 TPS 限流器实例。
 */
public class DbRateLimitRegistry {

    private final DbRateLimitProperties properties;
    private final Map<DbRateLimitLayer, LayerTpsRateLimiter> limiters = new EnumMap<>(DbRateLimitLayer.class);

    /**
     * 构造注入限流配置。
     */
    public DbRateLimitRegistry(DbRateLimitProperties properties) {
        this.properties = properties;
    }

    /**
     * 获取指定层级的 TPS 令牌（阻塞等待），未启用时直接返回。
     */
    public void acquire(DbRateLimitLayer layer, double annotationTps) {
        if (!properties.isEnabled()) {
            return;
        }
        double tps = properties.resolveTps(layer, annotationTps);
        limiters.computeIfAbsent(layer, ignored -> new LayerTpsRateLimiter(tps)).acquire();
    }
}
