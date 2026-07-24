package com.payment.common.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 账务系统 Dubbo 接入配置。
 */
@ConfigurationProperties(prefix = "pay.account")
public class PayAccountProperties {

    private boolean enabled = false;
    private String tenantId = "TENANT_DEMO";
    private String callerId = "ai-pay-settle";
    private String url = "dubbo://127.0.0.1:50051";
    private String postingMode = "LOCAL";
    private String currency = "CNY";
    private int timeoutMs = 3000;
    private int dispatchIntervalMs = 3000;

    public boolean isAccountOnly() {
        return enabled && "ACCOUNT_ONLY".equalsIgnoreCase(postingMode);
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getTenantId() {
        return tenantId;
    }

    public void setTenantId(String tenantId) {
        this.tenantId = tenantId;
    }

    public String getCallerId() {
        return callerId;
    }

    public void setCallerId(String callerId) {
        this.callerId = callerId;
    }

    public String getUrl() {
        return url;
    }

    public void setUrl(String url) {
        this.url = url;
    }

    public String getPostingMode() {
        return postingMode;
    }

    public void setPostingMode(String postingMode) {
        this.postingMode = postingMode;
    }

    public String getCurrency() {
        return currency;
    }

    public void setCurrency(String currency) {
        this.currency = currency;
    }

    public int getTimeoutMs() {
        return timeoutMs;
    }

    public void setTimeoutMs(int timeoutMs) {
        this.timeoutMs = timeoutMs;
    }

    public int getDispatchIntervalMs() {
        return dispatchIntervalMs;
    }

    public void setDispatchIntervalMs(int dispatchIntervalMs) {
        this.dispatchIntervalMs = dispatchIntervalMs;
    }
}
