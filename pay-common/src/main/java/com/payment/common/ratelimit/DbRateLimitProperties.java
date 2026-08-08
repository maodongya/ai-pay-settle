package com.payment.common.ratelimit;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 各层 DB 入口限流配置。
 * 注意：当前为单 JVM 限流，集群近似上限 ≈ 配置 TPS × 实例数。
 */
@ConfigurationProperties(prefix = "pay.db-rate-limit")
public class DbRateLimitProperties {

    private boolean enabled = true;
    private double accessTps = 30;
    private double calcTps = 30;
    private double settlementTps = 30;
    /** block=阻塞等待；reject=立即抛 RATE_LIMITED */
    private String mode = "block";

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public double getAccessTps() {
        return accessTps;
    }

    public void setAccessTps(double accessTps) {
        this.accessTps = accessTps;
    }

    public double getCalcTps() {
        return calcTps;
    }

    public void setCalcTps(double calcTps) {
        this.calcTps = calcTps;
    }

    public double getSettlementTps() {
        return settlementTps;
    }

    public void setSettlementTps(double settlementTps) {
        this.settlementTps = settlementTps;
    }

    public String getMode() {
        return mode;
    }

    public void setMode(String mode) {
        this.mode = mode;
    }

    double resolveTps(DbRateLimitLayer layer, double annotationTps) {
        if (annotationTps > 0) {
            return annotationTps;
        }
        return switch (layer) {
            case ACCESS -> accessTps;
            case CALC -> calcTps;
            case SETTLEMENT -> settlementTps;
        };
    }
}
