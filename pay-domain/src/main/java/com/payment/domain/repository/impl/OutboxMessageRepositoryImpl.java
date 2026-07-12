package com.payment.domain.repository.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.payment.domain.entity.OutboxMessageEntity;
import com.payment.domain.mapper.OutboxMessageMapper;
import com.payment.domain.repository.OutboxMessageRepository;
import com.payment.domain.support.MapperHelper;
import org.springframework.stereotype.Repository;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;

@Repository
public class OutboxMessageRepositoryImpl implements OutboxMessageRepository {

    private final OutboxMessageMapper outboxMessageMapper;

    public OutboxMessageRepositoryImpl(OutboxMessageMapper outboxMessageMapper) {
        this.outboxMessageMapper = outboxMessageMapper;
    }

    @Override
    public OutboxMessageEntity save(OutboxMessageEntity entity) {
        return MapperHelper.save(outboxMessageMapper, entity);
    }

    @Override
    public List<OutboxMessageEntity> findTop100ByStatusOrderByCreateTimeAsc(Integer status) {
        return findTopNByStatusOrderByCreateTimeAsc(status, 100);
    }

    @Override
    public List<OutboxMessageEntity> findTopNByStatusOrderByCreateTimeAsc(Integer status, int limit) {
        return outboxMessageMapper.selectList(new QueryWrapper<OutboxMessageEntity>()
                .eq("status", status)
                .orderByAsc("create_time")
                .last("LIMIT " + limit));
    }

    @Override
    public long countByStatus(Integer status) {
        return outboxMessageMapper.selectCount(new QueryWrapper<OutboxMessageEntity>()
                .eq("status", status));
    }

    @Override
    public long oldestPendingAgeSeconds() {
        OutboxMessageEntity oldest = outboxMessageMapper.selectOne(new QueryWrapper<OutboxMessageEntity>()
                .eq("status", 0)
                .orderByAsc("create_time")
                .last("LIMIT 1"));
        if (oldest == null || oldest.createTime == null) {
            return 0L;
        }
        return Duration.between(oldest.createTime, LocalDateTime.now()).getSeconds();
    }

    @Override
    public boolean existsByBizKey(String bizKey) {
        return outboxMessageMapper.selectCount(new QueryWrapper<OutboxMessageEntity>()
                .eq("biz_key", bizKey)) > 0;
    }
}
