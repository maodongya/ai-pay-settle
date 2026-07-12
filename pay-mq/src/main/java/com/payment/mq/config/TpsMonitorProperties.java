package com.payment.mq.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * TPS 对照监控阈值配置。
 */
@ConfigurationProperties(prefix = "pay.monitor.tps")
public class TpsMonitorProperties {

    private boolean enabled = true;
    private long checkIntervalMs = 60_000L;
    private long gapWarn = 30L;
    private long gapCritical = 100L;
    private double dropRatioWarn = 0.3;
    private double dropRatioCritical = 0.5;

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

    public long getGapWarn() {
        return gapWarn;
    }

    public void setGapWarn(long gapWarn) {
        this.gapWarn = gapWarn;
    }

    public long getGapCritical() {
        return gapCritical;
    }

    public void setGapCritical(long gapCritical) {
        this.gapCritical = gapCritical;
    }

    public double getDropRatioWarn() {
        return dropRatioWarn;
    }

    public void setDropRatioWarn(double dropRatioWarn) {
        this.dropRatioWarn = dropRatioWarn;
    }

    public double getDropRatioCritical() {
        return dropRatioCritical;
    }

    public void setDropRatioCritical(double dropRatioCritical) {
        this.dropRatioCritical = dropRatioCritical;
    }
}
