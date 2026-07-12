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

    int claimTask(String billNo, Integer expectedStatus, Integer newStatus, LocalDateTime now);

    List<ClearanceTaskEntity> findByStatusAndRetryCountLessThan(Integer status, Integer maxRetry);

    List<ClearanceTaskEntity> findByStatusAndUpdateTimeBefore(Integer status, LocalDateTime before);

    /** 统计指定状态任务数（积压监控） */
    long countByStatus(Integer status);

    /** 查询到达 next_retry_time 且未达最大重试次数的 FAILED 任务 */
    List<ClearanceTaskEntity> findFailedReadyForRetry(Integer status, Integer maxRetry, LocalDateTime now);
}
