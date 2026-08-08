package com.payment.calc.support; // 清算 MQ 发布组件包

import com.fasterxml.jackson.core.JsonProcessingException; // JSON 序列化异常
import com.fasterxml.jackson.databind.ObjectMapper; // JSON 工具
import com.payment.common.shard.ShardRouter;
import com.payment.mq.MqTags; // MQ Tag 常量
import com.payment.mq.MqTopics; // MQ Topic 常量
import com.payment.mq.PayMqProducer; // 统一生产者
import org.springframework.stereotype.Component; // Spring 组件

import java.time.LocalDateTime; // 时间戳
import java.util.HashMap; // 载荷 Map
import java.util.Map; // Map 接口

/**
 * 统一发布 clearance_task_topic 消息，供接入层与退款激活复用。
 */
@Component // 注册 Bean，BillAccessServiceImpl / ClearanceTaskServiceImpl 注入
public class ClearanceTaskPublisher {

    private final PayMqProducer payMqProducer; // MQ 生产者
    private final ObjectMapper objectMapper; // JSON 序列化

    /**
     * 构造注入生产者与 ObjectMapper。
     */
    public ClearanceTaskPublisher(PayMqProducer payMqProducer, ObjectMapper objectMapper) {
        this.payMqProducer = payMqProducer; // 保存生产者
        this.objectMapper = objectMapper; // 保存 JSON 工具
    }

    /**
     * 按 merchantId 有序发送清算任务消息。
     *
     * @param billNo     账单号
     * @param merchantId 商户 ID（hashKey，同商户进同 Queue）
     */
    public void publish(String billNo, Long merchantId) {
        Map<String, Object> payload = new HashMap<>(); // 构建 JSON 载荷
        payload.put("version", "1.0"); // 协议版本
        payload.put("billNo", billNo); // 账单号
        payload.put("merchantId", merchantId); // 商户 ID
        payload.put("shardId", ShardRouter.shardId(merchantId)); // 分片与 Queue 数对齐
        payload.put("createTime", LocalDateTime.now().toString()); // 创建时间
        try { // 序列化并发送
            String json = objectMapper.writeValueAsString(payload); // 转 JSON
            payMqProducer.sendOrderly(MqTopics.CLEARANCE_TASK, MqTags.TASK, // 有序发送到 clearance_task
                    String.valueOf(merchantId), json); // hashKey = merchantId
        } catch (JsonProcessingException e) { // 序列化失败
            throw new IllegalStateException("publish clearance task failed billNo=" + billNo, e); // 包装抛出
        }
    }
}
