package com.payment.mq;

public interface MqMessageHandler {

    String topic();

    void handle(String payload);
}
