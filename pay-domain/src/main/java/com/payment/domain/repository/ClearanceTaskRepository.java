package com.payment.domain.repository;

import com.payment.domain.entity.ClearanceTaskEntity;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * 清算任务仓储接口
 */
public interface ClearanceTaskRepository {

    ClearanceTaskEntity save(ClearanceTaskEntity entity);

    Optional<ClearanceTaskEntity> findByBillNo(String billNo);

    List<ClearanceTaskEntity> findByStatusOrderByCreateTimeAsc(Integer status);

    /** 按分片查询指定状态任务（Job 扫描） */
    List<ClearanceTaskEntity> findByStatusAndShardIdOrderByCreateTimeAsc(Integer status, int shardId, int limit);

    int claimTask(String billNo, Long merchantId, Integer expectedStatus, Integer newStatus, LocalDateTime now);

    List<ClearanceTaskEntity> findByStatusAndRetryCountLessThan(Integer status, Integer maxRetry);

    List<ClearanceTaskEntity> findByStatusAndUpdateTimeBefore(Integer status, LocalDateTime before);

    /** 按分片查询超时 RUNNING 任务（看门狗） */
    List<ClearanceTaskEntity> findByStatusAndShardIdAndUpdateTimeBefore(
            Integer status, int shardId, LocalDateTime before, int limit);

    /** 统计指定状态任务数（积压监控） */
    long countByStatus(Integer status);

    /** 查询到达 next_retry_time 且未达最大重试次数的 FAILED 任务 */
    List<ClearanceTaskEntity> findFailedReadyForRetry(Integer status, Integer maxRetry, LocalDateTime now);

    /** 按分片查询可重试 FAILED 任务 */
    List<ClearanceTaskEntity> findFailedReadyForRetryByShard(
            Integer status, Integer maxRetry, LocalDateTime now, int shardId, int limit);
}
