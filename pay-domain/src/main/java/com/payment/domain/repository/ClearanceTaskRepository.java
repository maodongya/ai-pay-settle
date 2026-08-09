package com.payment.domain.repository;

import com.payment.domain.entity.ClearanceTaskEntity;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * 清算任务仓储接口
 */
public interface ClearanceTaskRepository {

    /** 保存或更新清算任务 */
    ClearanceTaskEntity save(ClearanceTaskEntity entity);

    /** 幂等插入任务，已存在返回 false */
    boolean insertIfAbsent(String billNo, Long merchantId, int shardId, Integer status, LocalDateTime now);

    /** 将 FAILED 重置为 PENDING，成功返回 true */
    boolean resetToPending(String billNo, Long merchantId, Integer failedStatus,
                           Integer pendingStatus, int maxRetry, LocalDateTime now);

    /** 按账单号查询（带分片路由） */
    Optional<ClearanceTaskEntity> findByBillNo(String billNo);

    /** 按账单号与商户 ID 查询 */
    Optional<ClearanceTaskEntity> findByBillNoAndMerchantId(String billNo, Long merchantId);

    /** 按账单号与商户 ID 查询任务状态 */
    Optional<Integer> findStatusByBillNoAndMerchantId(String billNo, Long merchantId);

    /** 按状态查询任务，按创建时间升序 */
    List<ClearanceTaskEntity> findByStatusOrderByCreateTimeAsc(Integer status);

    /** 按分片查询指定状态任务（Job 扫描） */
    List<ClearanceTaskEntity> findByStatusAndShardIdOrderByCreateTimeAsc(Integer status, int shardId, int limit);

    /** 抢占任务（状态 CAS 更新） */
    int claimTask(String billNo, Long merchantId, Integer expectedStatus, Integer newStatus, LocalDateTime now);

    /** 标记任务成功 */
    int markSuccess(String billNo, Long merchantId, Integer expectedStatus, Integer newStatus, LocalDateTime now);

    /** 标记任务失败并安排重试（SQL 内原子 +1 retry 与退避时间） */
    int markFailed(String billNo, Long merchantId, Integer expectedStatus, Integer failedStatus,
                   Integer deadStatus, int maxRetry, String errorMsg, LocalDateTime now);

    /** 强制置为死信状态 */
    int markDead(String billNo, Long merchantId, Integer newStatus, String errorMsg, LocalDateTime now);

    /** 按状态与最大重试次数查询可重试任务 */
    List<ClearanceTaskEntity> findByStatusAndRetryCountLessThan(Integer status, Integer maxRetry);

    /** 按状态与更新时间上限查询超时任务 */
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
