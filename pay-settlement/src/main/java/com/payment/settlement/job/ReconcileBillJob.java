package com.payment.settlement.job; // 结算定时任务包

import com.payment.api.service.ReconcileService; // 对账服务接口
import org.springframework.scheduling.annotation.Scheduled; // 定时任务注解
import org.springframework.stereotype.Component; // Spring 组件注解

import java.time.LocalDate; // 本地日期

/**
 * 对账单生成定时任务。
 */
@Component // 注册为 Spring 组件
public class ReconcileBillJob {

    private final ReconcileService reconcileService; // 对账服务

    /**
     * 构造注入依赖。
     */
    public ReconcileBillJob(ReconcileService reconcileService) {
        this.reconcileService = reconcileService; // 赋值对账服务
    }

    /**
     * 定时生成前一日对账单。
     */
    @Scheduled(cron = "${pay.settle.reconcile-cron:0 0 4 * * ?}") // 默认每天 4 点执行
    public void generateDailyBills() {
        reconcileService.generateDailyBills(LocalDate.now().minusDays(1)); // 生成昨日对账单
    }
}
