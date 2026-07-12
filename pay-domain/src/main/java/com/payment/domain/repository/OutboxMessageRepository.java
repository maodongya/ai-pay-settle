package com.payment.domain.repository;

import com.payment.domain.entity.OutboxMessageEntity;

import java.util.List;

/**
 * 发件箱消息仓储接口
 */
public interface OutboxMessageRepository {

    OutboxMessageEntity save(OutboxMessageEntity entity);

    List<OutboxMessageEntity> findTop100ByStatusOrderByCreateTimeAsc(Integer status);
}
