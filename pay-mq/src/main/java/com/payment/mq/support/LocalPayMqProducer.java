package com.payment.mq.support; // Local 模式 MQ 生产者包

import com.payment.mq.PayMqProducer; // 生产者接口
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty; // 条件装配
import org.springframework.stereotype.Component; // Spring 组件

import java.util.concurrent.ConcurrentHashMap; // 按 hashKey 维护锁对象

/**
 * 本地模式 MQ 生产者：同步调用已注册的 {@link com.payment.mq.MqMessageHandler}。
 * sendOrderly 通过 per-hashKey 锁模拟同 Key 串行。
 */
@Component // 注册 Bean
@ConditionalOnProperty(name = "pay.mq.enabled", havingValue = "false", matchIfMissing = true) // 默认 Local 模式
public class LocalPayMqProducer implements PayMqProducer {

    private final LocalMqHandlerRegistry registry; // 本地 Handler 注册表
    /** hashKey → 锁对象，保证 Local 模式下同一 Key 串行消费 */
    private final ConcurrentHashMap<String, Object> orderlyLocks = new ConcurrentHashMap<>(); // 有序锁缓存

    /**
     * 构造注入注册表。
     */
    public LocalPayMqProducer(LocalMqHandlerRegistry registry) {
        this.registry = registry; // 保存注册表
    }

    @Override // 普通发送
    public void send(String topic, String payload) {
        send(topic, null, null, payload); // 委托四参数
    }

    @Override // 带 Tag 发送
    public void send(String topic, String tag, String payload) {
        send(topic, tag, null, payload); // 委托四参数
    }

    @Override // 带 Tag/Keys 发送（Local 忽略 tag/keys，按 topic 分发）
    public void send(String topic, String tag, String keys, String payload) {
        registry.dispatch(topic, payload); // 同步调用 Handler
    }

    @Override // 有序发送：同 hashKey 加锁后 dispatch
    public void sendOrderly(String topic, String tag, String hashKey, String payload) {
        String key = hashKey != null ? hashKey : ""; // 空 Key 归一化
        Object lock = orderlyLocks.computeIfAbsent(key, k -> new Object()); // 获取或创建锁
        synchronized (lock) { // 同 Key 串行
            registry.dispatch(topic, payload); // 同步调用 Handler
        }
    }
}
