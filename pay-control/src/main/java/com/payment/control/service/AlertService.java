package com.payment.control.service; // 管控服务包

import com.payment.domain.entity.AlertRecordEntity; // 告警记录实体
import com.payment.domain.repository.AlertRecordRepository; // 告警记录仓储
import org.slf4j.Logger; // 日志接口
import org.slf4j.LoggerFactory; // 日志工厂
import org.springframework.stereotype.Service; // Spring 服务注解

import java.time.LocalDateTime; // 本地日期时间

/**
 * 告警服务，记录并输出系统告警信息。
 */
@Service // 注册为 Spring 服务
public class AlertService {

    public static final String PAYMENT_FAIL = "PAYMENT_FAIL"; // 支付失败告警类型
    public static final int LEVEL_ERROR = 3; // 错误级别
    public static final int LEVEL_WARN = 2; // 警告级别

    private static final Logger log = LoggerFactory.getLogger(AlertService.class); // 日志记录器

    private final AlertRecordRepository alertRecordRepository; // 告警记录仓储

    /**
     * 构造注入依赖。
     */
    public AlertService(AlertRecordRepository alertRecordRepository) {
        this.alertRecordRepository = alertRecordRepository; // 赋值告警仓储
    }

    /**
     * 发送错误级别告警。
     */
    public void send(String alertType, String content) {
        send(alertType, LEVEL_ERROR, content); // 默认错误级别
    }

    /**
     * 发送指定级别的告警。
     */
    public void send(String alertType, int level, String content) {
        AlertRecordEntity alert = new AlertRecordEntity(); // 创建告警记录
        alert.alertType = alertType; // 告警类型
        alert.level = level; // 告警级别
        alert.content = content; // 告警内容
        alert.status = 0; // 未处理状态
        alert.createTime = LocalDateTime.now(); // 创建时间
        alertRecordRepository.save(alert); // 保存告警
        log.warn("alert type={} content={}", alertType, content); // 输出警告日志
    }
}
