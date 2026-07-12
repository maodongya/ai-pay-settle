package com.payment.mq.support; // MQ Listener 统一调用代理包

import com.payment.mq.support.MqConsumeExceptionClassifier.MqConsumeAction; // 消费处置动作枚举
import org.slf4j.Logger; // 日志接口
import org.slf4j.LoggerFactory; // 日志工厂
import org.springframework.stereotype.Component; // Spring 组件

/**
 * 统一包装 RocketMQ Listener 的 onMessage，接入异常分类与（可选）指标埋点。
 */
@Component // 供各模块 Consumer 内部 Listener 注入
public class MqListenerInvoker {

    private static final Logger log = LoggerFactory.getLogger(MqListenerInvoker.class); // 本类日志

    private final MqConsumeExceptionClassifier classifier; // 异常 → RETRY/ACK/DLQ 分类器
    private final MqConsumeMetrics metrics; // 消费耗时与成功/失败计数

    /**
     * 构造注入分类器与指标组件。
     */
    public MqListenerInvoker(MqConsumeExceptionClassifier classifier, MqConsumeMetrics metrics) {
        this.classifier = classifier; // 保存分类器
        this.metrics = metrics; // 保存指标组件
    }

    /**
     * 执行消费逻辑并根据分类结果决定 ACK 策略。
     *
     * @param topic  消息 Topic，用于日志与指标标签
     * @param action 实际业务处理（通常为 delegate.handle）
     */
    public void invoke(String topic, Runnable action) {
        try { // 尝试执行业务并记录指标
            metrics.recordVoid(topic, action); // 计时包裹执行
        } catch (Exception e) { // 捕获所有消费异常
            MqConsumeAction decision = classifier.classify(e); // 分类处置动作
            switch (decision) { // 按动作分支
                case RETRY -> { // 需要 MQ 重试
                    log.warn("mq consume retry topic={} msg={}", topic, e.getMessage()); // 记录重试日志
                    rethrow(e); // 原样抛出触发 Client 重试
                }
                case ACK -> log.warn("mq consume ack despite error topic={} msg={}", topic, e.getMessage()); // 幂等/等待，直接 ACK
                case DLQ -> { // 应快速进入 DLQ
                    log.error("mq consume dlq topic={} msg={}", topic, e.getMessage(), e); // 记录错误栈
                    rethrow(e); // 抛出以进入 DLQ（配合 maxReconsumeTimes）
                }
            }
        }
    }

    /**
     * 将受检异常转为运行时异常重新抛出，保持 RocketMQ 重试语义。
     */
    private void rethrow(Exception e) {
        if (e instanceof RuntimeException runtime) { // 已是运行时异常
            throw runtime; // 直接抛出
        }
        throw new IllegalStateException("mq consume failed", e); // 包装为运行时异常
    }
}
