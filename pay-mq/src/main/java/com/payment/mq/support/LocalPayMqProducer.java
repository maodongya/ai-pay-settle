package com.payment.mq.support;

import com.payment.mq.PayMqProducer;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "pay.mq.enabled", havingValue = "false", matchIfMissing = true)
public class LocalPayMqProducer implements PayMqProducer {

    private final LocalMqHandlerRegistry registry;

    public LocalPayMqProducer(LocalMqHandlerRegistry registry) {
        this.registry = registry;
    }

    @Override
    public void send(String topic, String payload) {
        send(topic, null, null, payload);
    }

    @Override
    public void send(String topic, String tag, String payload) {
        send(topic, tag, null, payload);
    }

    @Override
    public void send(String topic, String tag, String keys, String payload) {
        registry.dispatch(topic, payload);
    }
}
