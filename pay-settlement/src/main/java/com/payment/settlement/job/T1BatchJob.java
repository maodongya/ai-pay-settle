package com.payment.settlement.job; // 结算定时任务包

import com.payment.api.service.SettleAccountService; // 结算账户服务接口
import org.springframework.scheduling.annotation.Scheduled; // 定时任务注解
import org.springframework.stereotype.Component; // Spring 组件注解

import java.time.LocalDate; // 本地日期

/**
 * T+1 批量结算定时任务。
 */
@Component // 注册为 Spring 组件
public class T1BatchJob {

    private final SettleAccountService settleAccountService; // 结算账户服务

    /**
     * 构造注入依赖。
     */
    public T1BatchJob(SettleAccountService settleAccountService) {
        this.settleAccountService = settleAccountService; // 赋值结算服务
    }

    /**
     * 定时执行 T+1 批量结算。
     */
    @Scheduled(cron = "${pay.settle.t1-cron:0 0 2 * * ?}") // 默认每天 2 点执行
    public void runT1Batch() {
        settleAccountService.runT1Batch(LocalDate.now()); // 执行当日 T+1 批次
    }
}
