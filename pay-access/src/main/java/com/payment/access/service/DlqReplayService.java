package com.payment.access.service; // 接入/运营服务包

import com.payment.mq.PayMqProducer; // MQ 生产者（重放到原 Topic）
import org.slf4j.Logger; // 日志
import org.slf4j.LoggerFactory; // 日志工厂
import org.springframework.stereotype.Service; // 服务

/**
 * DLQ 人工重放服务：审核后将消息体重新投递到原 Topic（须保证幂等）。
 */
@Service // 注册 Bean
public class DlqReplayService {

    private static final Logger log = LoggerFactory.getLogger(DlqReplayService.class); // 日志

    private final PayMqProducer payMqProducer; // 生产者

    /** 构造注入 */
    public DlqReplayService(PayMqProducer payMqProducer) {
        this.payMqProducer = payMqProducer; // 保存生产者
    }

    /**
     * 人工重放 DLQ 消息到目标 Topic。
     *
     * @param targetTopic 原 Topic 名
     * @param tag         消息 Tag，可为 null
     * @param hashKey     有序 Topic 时的 hashKey，可为 null
     * @param payload     消息 JSON 体
     * @param operatorId  操作人 ID（审计）
     * @param remark      备注
     */
    public void replay(String targetTopic, String tag, String hashKey, String payload, Long operatorId, String remark) {
        log.info("dlq replay topic={} operator={} remark={}", targetTopic, operatorId, remark); // 审计日志
        if (hashKey != null && !hashKey.isBlank()) { // 有序 Topic
            payMqProducer.sendOrderly(targetTopic, tag, hashKey, payload); // 有序重放
        } else { // 普通 Topic
            payMqProducer.send(targetTopic, tag, null, payload); // 普通重放
        }
    }
}
