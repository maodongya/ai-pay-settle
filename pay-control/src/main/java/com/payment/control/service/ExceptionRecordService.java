package com.payment.control.service; // 管控服务包

import com.payment.domain.entity.ExceptionRecordEntity; // 工单实体
import com.payment.domain.repository.ExceptionRecordRepository; // 工单仓储
import org.springframework.stereotype.Service; // 服务注解

import java.time.LocalDateTime; // 时间
import java.time.format.DateTimeFormatter; // 格式化
import java.util.concurrent.atomic.AtomicLong; // 序号生成

/**
 * 异常工单服务：清算 DEAD、DLQ 等非自愈场景自动建单。
 */
@Service // 注册 Bean
public class ExceptionRecordService {

    public static final int SEVERITY_P1 = 1; // P1 严重
    public static final int SEVERITY_P2 = 2; // P2 告警

    private static final AtomicLong SEQ = new AtomicLong(System.currentTimeMillis() % 100_000); // 简易序号

    private final ExceptionRecordRepository exceptionRecordRepository; // 仓储

    /** 构造注入 */
    public ExceptionRecordService(ExceptionRecordRepository exceptionRecordRepository) {
        this.exceptionRecordRepository = exceptionRecordRepository; // 保存仓储
    }

    /**
     * 打开异常工单（若同 bizKey+code 已存在未关闭则跳过）。
     *
     * @param exceptionCode 如 EX-0201
     * @param bizDomain     如 CLEAR / MQ
     * @param bizKey        billNo 或 consumerGroup
     * @param detail        详情文本
     */
    public void openIfAbsent(String exceptionCode, String bizDomain, String bizKey, String detail) {
        if (exceptionRecordRepository.findOpenByBizKeyAndCode(bizKey, exceptionCode).isPresent()) { // 已存在
            return; // 幂等跳过
        }
        ExceptionRecordEntity record = new ExceptionRecordEntity(); // 新建实体
        record.exceptionNo = nextExceptionNo(); // 生成工单号
        record.exceptionCode = exceptionCode; // 异常码
        record.severity = exceptionCode.startsWith("EX-01") ? SEVERITY_P1 : SEVERITY_P2; // P1/P2
        record.bizDomain = bizDomain; // 业务域
        record.bizKey = bizKey; // 业务键
        record.title = exceptionCode + " " + bizKey; // 标题
        record.detail = detail; // 详情
        record.status = 0; // 待处理
        record.createTime = LocalDateTime.now(); // 创建时间
        exceptionRecordRepository.save(record); // 持久化
    }

    /** 清算 DEAD 专用建单 */
    public void openClearanceDead(String billNo, String errorMsg) {
        openIfAbsent("EX-0201", "CLEAR", billNo, errorMsg != null ? errorMsg : "clearance dead"); // EX-0201
    }

    /** 生成 EX + 日期 + 序号 工单号 */
    private String nextExceptionNo() {
        String day = LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd")); // 日期部分
        long seq = SEQ.incrementAndGet() % 100_000; // 递增序号
        return "EX" + day + String.format("%05d", seq); // 拼接工单号
    }
}
