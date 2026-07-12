package com.payment.domain.repository;

import com.payment.domain.entity.OutboxMessageEntity;

import java.util.List;

/**
 * 发件箱消息仓储接口
 */
public interface OutboxMessageRepository {

    OutboxMessageEntity save(OutboxMessageEntity entity);

    List<OutboxMessageEntity> findTop100ByStatusOrderByCreateTimeAsc(Integer status);

    /** 按状态查询前 N 条，按创建时间升序（Outbox 批量派发） */
    List<OutboxMessageEntity> findTopNByStatusOrderByCreateTimeAsc(Integer status, int limit);

    /** 统计指定状态的 Outbox 行数（积压监控） */
    long countByStatus(Integer status);

    /** 按业务键查询（补偿 Job 判断是否已有 Outbox） */
    boolean existsByBizKey(String bizKey);
}
