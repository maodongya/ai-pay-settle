package com.payment.calc.job; // 清算定时任务包

import com.payment.api.service.ClearanceTaskService; // 清算任务服务接口
import com.payment.common.enums.TaskStatus; // 任务状态枚举
import com.payment.domain.entity.ClearanceTaskEntity; // 清算任务实体
import com.payment.domain.repository.ClearanceTaskRepository; // 清算任务仓储
import com.payment.domain.support.ShardScanSupport;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled; // 定时任务注解
import org.springframework.stereotype.Component; // Spring 组件注解

import java.time.LocalDateTime; // 本地日期时间
import java.util.ArrayList;
import java.util.List; // 列表

/**
 * 清算重试与看门狗定时任务（16 分片并行扫描）。
 */
@Component // 注册为 Spring 组件
public class ClearanceRetryJob {

    private static final int WATCHDOG_BATCH_PER_SHARD = 50;

    private final ClearanceTaskService clearanceTaskService; // 清算任务服务
    private final ClearanceTaskRepository clearanceTaskRepository; // 清算任务仓储
    private final boolean pauseJobs;

    /**
     * 构造注入依赖。
     */
    public ClearanceRetryJob(ClearanceTaskService clearanceTaskService,
                             ClearanceTaskRepository clearanceTaskRepository,
                             @Value("${pay.loadtest.pause-jobs:false}") boolean pauseJobs) {
        this.clearanceTaskService = clearanceTaskService; // 赋值任务服务
        this.clearanceTaskRepository = clearanceTaskRepository; // 赋值任务仓储
        this.pauseJobs = pauseJobs;
    }

    /**
     * 定时重试失败的清算任务。
     */
    @Scheduled(fixedDelayString = "${pay.clearance.retry-interval-ms:3600000}") // 默认每小时执行
    public void retryFailed() {
        if (pauseJobs) {
            return;
        }
        clearanceTaskService.retryFailedTasks(100); // 最多重试 100 条
    }

    /**
     * 看门狗：将长时间运行中的任务标记为失败（按分片扫描）。
     */
    @Scheduled(fixedDelayString = "${pay.clearance.watchdog-interval-ms:300000}") // 默认每 5 分钟执行
    public void watchdog() {
        if (pauseJobs) {
            return;
        }
        LocalDateTime threshold = LocalDateTime.now().minusMinutes(30);
        List<ClearanceTaskEntity> stale = new ArrayList<>();
        ShardScanSupport.forEachShard(shardId -> stale.addAll(
                clearanceTaskRepository.findByStatusAndShardIdAndUpdateTimeBefore(
                        TaskStatus.RUNNING.getCode(), shardId, threshold, WATCHDOG_BATCH_PER_SHARD)));
        for (ClearanceTaskEntity task : stale) { // 逐个处理
            task.status = TaskStatus.FAILED.getCode(); // 标记失败
            task.errorMsg = "watchdog timeout";
            clearanceTaskRepository.save(task); // 保存任务
        }
    }
}
