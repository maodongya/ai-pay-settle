package com.payment.settlement.job; // 结算定时任务包

import com.payment.api.service.SettleAccountService; // 结算账户服务接口
import org.springframework.scheduling.annotation.Scheduled; // 定时任务注解
import org.springframework.stereotype.Component; // Spring 组件注解

/**
 * 支付重试定时任务。
 */
@Component // 注册为 Spring 组件
public class PaymentRetryJob {

    private final SettleAccountService settleAccountService; // 结算账户服务

    /**
     * 构造注入依赖。
     */
    public PaymentRetryJob(SettleAccountService settleAccountService) {
        this.settleAccountService = settleAccountService; // 赋值结算服务
    }

    /**
     * 定时重试失败的支付订单。
     */
    @Scheduled(cron = "${pay.settle.payment-retry-cron:0 30 * * * ?}") // 默认每小时 30 分执行
    public void retryFailedPayments() {
        settleAccountService.retryFailedPayments(50); // 最多重试 50 条
    }
}
