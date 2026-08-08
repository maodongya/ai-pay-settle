package com.payment.mq.support;

import com.payment.mq.MqMessageHandler;
import org.springframework.beans.factory.ObjectProvider;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 本地模式 Handler 注册表：按 Topic 路由到 {@link com.payment.mq.MqMessageHandler}。
 */
@Component
public class LocalMqHandlerRegistry {

    private final ObjectProvider<List<MqMessageHandler>> handlerProvider;
    private volatile Map<String, MqMessageHandler> handlers;

    /**
     * 构造注入 Handler 列表提供者（延迟加载）。
     */
    public LocalMqHandlerRegistry(ObjectProvider<List<MqMessageHandler>> handlerProvider) {
        this.handlerProvider = handlerProvider;
    }

    /**
     * 按 Topic 同步分发消息到对应 Handler。
     */
    public void dispatch(String topic, String payload) {
        MqMessageHandler handler = handlerMap().get(topic);
        if (handler == null) {
            throw new IllegalStateException("no local mq handler for topic: " + topic);
        }
        handler.handle(payload);
    }

    private Map<String, MqMessageHandler> handlerMap() {
        Map<String, MqMessageHandler> current = handlers;
        if (current == null) {
            synchronized (this) {
                current = handlers;
                if (current == null) {
                    Map<String, MqMessageHandler> map = new ConcurrentHashMap<>();
                    for (MqMessageHandler handler : handlerProvider.getIfAvailable(List::of)) {
                        map.put(handler.topic(), handler);
                    }
                    handlers = current = map;
                }
            }
        }
        return current;
    }
}
