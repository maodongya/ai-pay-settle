package com.payment.common.cache;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Redis 开关与相关配置。
 */
@ConfigurationProperties(prefix = "payment.redis")
public class PaymentRedisProperties {

    /** 是否启用 Redis（缓存/序列号/分布式锁） */
    private boolean enabled = true;

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }
}
