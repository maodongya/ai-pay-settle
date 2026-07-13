package com.payment.split.job; // 补偿 Job 包

import com.fasterxml.jackson.core.JsonProcessingException; // JSON 异常
import com.fasterxml.jackson.databind.ObjectMapper; // JSON 工具
import com.payment.common.enums.TaskStatus; // 任务状态
import com.payment.domain.entity.ClearanceTaskEntity; // 清算任务
import com.payment.domain.entity.OutboxMessageEntity; // Outbox
import com.payment.domain.repository.ClearanceTaskRepository; // 任务仓储
import com.payment.domain.repository.FeeCalcResultRepository; // 计费仓储
import com.payment.domain.repository.OutboxMessageRepository; // Outbox 仓储
import com.payment.domain.repository.SplitDetailRepository; // 分账仓储
import org.slf4j.Logger; // 日志
import org.slf4j.LoggerFactory; // 日志工厂
import org.springframework.scheduling.annotation.Scheduled; // 定时
import org.springframework.stereotype.Component; // 组件

import java.math.BigDecimal; // 金额
import java.time.LocalDateTime; // 时间
import java.util.HashMap; // 载荷 Map
import java.util.List; // 列表
import java.util.Map; // Map

/**
 * 补偿 Job：有 split_detail 但缺少 outbox_message 时补写 Outbox（EX-0207 场景）。
 */
@Component // 注册 Bean
public class OutboxCompensateJob {

    private static final Logger log = LoggerFactory.getLogger(OutboxCompensateJob.class); // 日志
    private static final String SETTLE_TOPIC = "settle_amount_topic"; // 结算 Topic

    private final ClearanceTaskRepository clearanceTaskRepository; // 任务
    private final FeeCalcResultRepository feeCalcResultRepository; // 计费结果
    private final SplitDetailRepository splitDetailRepository; // 分账
    private final OutboxMessageRepository outboxMessageRepository; // Outbox
    private final ObjectMapper objectMapper; // JSON

    /** 构造注入 */
    public OutboxCompensateJob(ClearanceTaskRepository clearanceTaskRepository,
                               FeeCalcResultRepository feeCalcResultRepository,
                               SplitDetailRepository splitDetailRepository,
                               OutboxMessageRepository outboxMessageRepository,
                               ObjectMapper objectMapper) {
        this.clearanceTaskRepository = clearanceTaskRepository; // 任务仓储
        this.feeCalcResultRepository = feeCalcResultRepository; // 计费仓储
        this.splitDetailRepository = splitDetailRepository; // 分账仓储
        this.outboxMessageRepository = outboxMessageRepository; // Outbox 仓储
        this.objectMapper = objectMapper; // JSON
    }

    /** 定时扫描并补 Outbox */
    @Scheduled(fixedDelayString = "${pay.compensate.outbox-interval-ms:600000}") // 默认 10 分钟
    public void compensateMissingOutbox() {
        List<ClearanceTaskEntity> candidates = clearanceTaskRepository // 成功或失败但有 split 的任务
                .findByStatusOrderByCreateTimeAsc(TaskStatus.SUCCESS.getCode());
        for (ClearanceTaskEntity task : candidates) { // 遍历
            compensateOne(task.billNo); // 尝试补偿单条
        }
    }

    /** 单 billNo 补偿逻辑 */
    private void compensateOne(String billNo) {
        if (!splitDetailRepository.existsByBillNo(billNo)) { // 无分账
            return; // 跳过
        }
        if (outboxMessageRepository.existsByBizKey(billNo)) { // 已有 Outbox（任意状态）
            return; // 跳过
        }
        feeCalcResultRepository.findByBillNo(billNo).ifPresent(result -> { // 有计费结果
            if (result.merchantIncome == null || result.merchantIncome.compareTo(BigDecimal.ZERO) == 0) { // 无入账
                return; // 跳过
            }
            try { // 补写 Outbox
                OutboxMessageEntity outbox = new OutboxMessageEntity(); // 实体
                outbox.bizKey = billNo; // 业务键
                outbox.merchantId = result.merchantId; // 分片键
                outbox.topic = SETTLE_TOPIC; // Topic
                outbox.payload = buildSettlePayload(result); // JSON
                outbox.status = 0; // 待发送
                outbox.createTime = LocalDateTime.now(); // 时间
                outboxMessageRepository.save(outbox); // 保存
                log.info("outbox compensated billNo={}", billNo); // 成功日志
            } catch (JsonProcessingException e) { // JSON 失败
                log.warn("outbox compensate json failed billNo={}", billNo, e); // 警告
            }
        });
    }

    /** 构建 settle_amount 载荷（与 SplitServiceImpl 一致） */
    private String buildSettlePayload(com.payment.domain.entity.FeeCalcResultEntity r) throws JsonProcessingException {
        Map<String, Object> map = new HashMap<>(); // Map
        map.put("merchantId", r.merchantId); // 商户
        map.put("billNo", r.billNo); // 账单
        map.put("amount", r.merchantIncome.toPlainString()); // 金额字符串
        return objectMapper.writeValueAsString(map); // JSON
    }
}
