package com.payment.mq.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "pay.mq")
public class PayMqProperties {

    private boolean enabled = false;
    private String nameServer = "127.0.0.1:9876";
    private String producerGroup = "pay-settle-producer";
    private boolean outboxViaMq = true;
    private boolean clearanceViaMq = true;
    private boolean paymentCallbackViaMq = true;

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public String getNameServer() {
        return nameServer;
    }

    public void setNameServer(String nameServer) {
        this.nameServer = nameServer;
    }

    public String getProducerGroup() {
        return producerGroup;
    }

    public void setProducerGroup(String producerGroup) {
        this.producerGroup = producerGroup;
    }

    public boolean isOutboxViaMq() {
        return outboxViaMq;
    }

    public void setOutboxViaMq(boolean outboxViaMq) {
        this.outboxViaMq = outboxViaMq;
    }

    public boolean isClearanceViaMq() {
        return clearanceViaMq;
    }

    public void setClearanceViaMq(boolean clearanceViaMq) {
        this.clearanceViaMq = clearanceViaMq;
    }

    public boolean isPaymentCallbackViaMq() {
        return paymentCallbackViaMq;
    }

    public void setPaymentCallbackViaMq(boolean paymentCallbackViaMq) {
        this.paymentCallbackViaMq = paymentCallbackViaMq;
    }
}
