package com.payment.mq.support;

import com.payment.mq.MqMessageHandler;
import com.payment.mq.MqTopics;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.ObjectProvider;

import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;

class LocalPayMqProducerTest {

    @Test
    void shouldDispatchToRegisteredHandler() {
        AtomicInteger counter = new AtomicInteger();
        MqMessageHandler handler = new MqMessageHandler() {
            @Override
            public String topic() {
                return MqTopics.SETTLE_AMOUNT;
            }

            @Override
            public void handle(String payload) {
                counter.incrementAndGet();
            }
        };
        LocalMqHandlerRegistry registry = new LocalMqHandlerRegistry(fixedProvider(List.of(handler)));
        LocalPayMqProducer producer = new LocalPayMqProducer(registry);

        producer.send(MqTopics.SETTLE_AMOUNT, "{\"billNo\":\"CL001\"}");

        assertEquals(1, counter.get());
    }

    @Test
    void shouldSerializeOrderlyByHashKey() {
        AtomicInteger concurrent = new AtomicInteger();
        MqMessageHandler handler = new MqMessageHandler() {
            @Override
            public String topic() {
                return MqTopics.SETTLE_AMOUNT;
            }

            @Override
            public void handle(String payload) {
                concurrent.incrementAndGet();
            }
        };
        LocalMqHandlerRegistry registry = new LocalMqHandlerRegistry(fixedProvider(List.of(handler)));
        LocalPayMqProducer producer = new LocalPayMqProducer(registry);

        producer.sendOrderly(MqTopics.SETTLE_AMOUNT, null, "m1", "{\"a\":1}");
        producer.sendOrderly(MqTopics.SETTLE_AMOUNT, null, "m1", "{\"a\":2}");

        assertEquals(2, concurrent.get());
    }

    private static ObjectProvider<List<MqMessageHandler>> fixedProvider(List<MqMessageHandler> handlers) {
        return new ObjectProvider<>() {
            @Override
            public List<MqMessageHandler> getObject() {
                return handlers;
            }

            @Override
            public List<MqMessageHandler> getObject(Object... args) {
                return handlers;
            }

            @Override
            public List<MqMessageHandler> getIfAvailable() {
                return handlers;
            }

            @Override
            public List<MqMessageHandler> getIfUnique() {
                return handlers;
            }

            @Override
            public List<MqMessageHandler> getIfAvailable(Supplier<List<MqMessageHandler>> defaultSupplier) {
                return handlers;
            }

            @Override
            public Stream<List<MqMessageHandler>> stream() {
                return Stream.of(handlers);
            }

            @Override
            public Stream<List<MqMessageHandler>> orderedStream() {
                return Stream.of(handlers);
            }
        };
    }
}
