package com.payment.common.ratelimit;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 各层 DB 入口限流配置。
 */
@ConfigurationProperties(prefix = "pay.db-rate-limit")
public class DbRateLimitProperties {

    /** 是否启用限流 */
    private boolean enabled = true;

    /** 接入层 TPS 上限 */
    private double accessTps = 30;

    /** 清算层 TPS 上限 */
    private double calcTps = 30;

    /** 结算层 TPS 上限 */
    private double settlementTps = 30;

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
