package com.payment.domain.repository.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.payment.domain.entity.OutboxMessageEntity;
import com.payment.domain.mapper.OutboxMessageMapper;
import com.payment.domain.repository.OutboxMessageRepository;
import com.payment.domain.service.ShardRouteService;
import com.payment.domain.support.MapperHelper;
import com.payment.domain.support.ShardQueryHelper;
import org.springframework.stereotype.Repository;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;

/**
 * {@link OutboxMessageRepository} 的 MyBatis-Plus 实现。
 */
@Repository
public class OutboxMessageRepositoryImpl implements OutboxMessageRepository {

    private final OutboxMessageMapper outboxMessageMapper;
    private final ShardRouteService shardRouteService;

    public OutboxMessageRepositoryImpl(OutboxMessageMapper outboxMessageMapper,
                                       ShardRouteService shardRouteService) {
        this.outboxMessageMapper = outboxMessageMapper;
        this.shardRouteService = shardRouteService;
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

    /** 按 merchant_id 取模过滤分片 */
    @Override
    public List<OutboxMessageEntity> findTopNByStatusAndShardIdOrderByCreateTimeAsc(
            Integer status, int shardId, int limit) {
        QueryWrapper<OutboxMessageEntity> wrapper = new QueryWrapper<OutboxMessageEntity>()
                .eq("status", status);
        ShardQueryHelper.eqShardId(wrapper, shardId);
        return outboxMessageMapper.selectList(wrapper
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

    /** 有路由时补 merchant_id 避免全分片广播 */
    @Override
    public boolean existsByBizKey(String bizKey) {
        // outbox 业务键列是 biz_key（非 bill_no）；有路由时补 merchant_id 避免广播
        QueryWrapper<OutboxMessageEntity> wrapper = new QueryWrapper<OutboxMessageEntity>()
                .eq("biz_key", bizKey);
        shardRouteService.findMerchantIdByBillNo(bizKey).ifPresent(id -> wrapper.eq("merchant_id", id));
        return outboxMessageMapper.selectCount(wrapper) > 0;
    }

    @Override
    public int markSentByIdAndMerchantId(Long id, Long merchantId) {
        return outboxMessageMapper.markSentByIdAndMerchantId(id, merchantId);
    }
}
