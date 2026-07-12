package com.payment.domain.repository.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.payment.domain.entity.OutboxMessageEntity;
import com.payment.domain.mapper.OutboxMessageMapper;
import com.payment.domain.repository.OutboxMessageRepository;
import com.payment.domain.support.MapperHelper;
import org.springframework.stereotype.Repository;

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
        return outboxMessageMapper.selectList(new QueryWrapper<OutboxMessageEntity>()
                .eq("status", status)
                .orderByAsc("create_time")
                .last("LIMIT 100"));
    }
}
