package com.payment.control.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 数据库容量监控配置。
 */
@ConfigurationProperties(prefix = "pay.monitor.db")
public class DbMonitorProperties {

    private boolean enabled = true;
    private long checkIntervalMs = 300_000L;

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public long getCheckIntervalMs() {
        return checkIntervalMs;
    }

    public void setCheckIntervalMs(long checkIntervalMs) {
        this.checkIntervalMs = checkIntervalMs;
    }
}
