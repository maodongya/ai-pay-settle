package com.payment.domain.repository.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.payment.domain.entity.ClearanceTaskEntity;
import com.payment.domain.mapper.ClearanceTaskMapper;
import com.payment.domain.repository.ClearanceTaskRepository;
import com.payment.domain.service.ShardRouteService;
import com.payment.domain.support.MapperHelper;
import com.payment.domain.support.ShardQueryHelper;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * {@link ClearanceTaskRepository} 的 MyBatis-Plus 实现。
 */
@Repository
public class ClearanceTaskRepositoryImpl implements ClearanceTaskRepository {

    private final ClearanceTaskMapper clearanceTaskMapper;
    private final ShardRouteService shardRouteService;

    public ClearanceTaskRepositoryImpl(ClearanceTaskMapper clearanceTaskMapper,
                                       ShardRouteService shardRouteService) {
        this.clearanceTaskMapper = clearanceTaskMapper;
        this.shardRouteService = shardRouteService;
    }

    @Override
    public ClearanceTaskEntity save(ClearanceTaskEntity entity) {
        return MapperHelper.save(clearanceTaskMapper, entity);
    }

    /** 通过 bill_route 补全 merchant_id 精准路由 */
    @Override
    public Optional<ClearanceTaskEntity> findByBillNo(String billNo) {
        QueryWrapper<ClearanceTaskEntity> wrapper = new QueryWrapper<>();
        ShardQueryHelper.byBillNo(wrapper, billNo, shardRouteService);
        return Optional.ofNullable(clearanceTaskMapper.selectOne(wrapper));
    }

    @Override
    public Optional<ClearanceTaskEntity> findByBillNoAndMerchantId(String billNo, Long merchantId) {
        return Optional.ofNullable(clearanceTaskMapper.selectOne(new QueryWrapper<ClearanceTaskEntity>()
                .eq("bill_no", billNo)
                .eq("merchant_id", merchantId)));
    }

    @Override
    public Optional<Integer> findStatusByBillNoAndMerchantId(String billNo, Long merchantId) {
        return Optional.ofNullable(clearanceTaskMapper.selectStatusByBillNoAndMerchantId(billNo, merchantId));
    }

    @Override
    public List<ClearanceTaskEntity> findByStatusOrderByCreateTimeAsc(Integer status) {
        return clearanceTaskMapper.selectList(new QueryWrapper<ClearanceTaskEntity>()
                .eq("status", status)
                .orderByAsc("create_time"));
    }

    @Override
    public List<ClearanceTaskEntity> findByStatusAndShardIdOrderByCreateTimeAsc(Integer status, int shardId, int limit) {
        return clearanceTaskMapper.selectList(new QueryWrapper<ClearanceTaskEntity>()
                .eq("status", status)
                .eq("shard_id", shardId)
                .orderByAsc("create_time")
                .last("LIMIT " + limit));
    }

    @Override
    @Transactional
    public int claimTask(String billNo, Long merchantId, Integer expectedStatus, Integer newStatus, LocalDateTime now) {
        return clearanceTaskMapper.claimTask(billNo, merchantId, expectedStatus, newStatus, now);
    }

    @Override
    @Transactional
    public int markSuccess(String billNo, Long merchantId, Integer expectedStatus, Integer newStatus, LocalDateTime now) {
        return clearanceTaskMapper.markSuccess(billNo, merchantId, expectedStatus, newStatus, now);
    }

    @Override
    @Transactional
    public int markFailed(String billNo, Long merchantId, Integer expectedStatus, Integer failedStatus,
                          Integer deadStatus, int maxRetry, String errorMsg,
                          LocalDateTime nextRetryTime, LocalDateTime now) {
        return clearanceTaskMapper.markFailed(billNo, merchantId, expectedStatus, failedStatus, deadStatus,
                maxRetry, errorMsg, nextRetryTime, now);
    }

    @Override
    @Transactional
    public int markDead(String billNo, Long merchantId, Integer newStatus, String errorMsg, LocalDateTime now) {
        return clearanceTaskMapper.markDead(billNo, merchantId, newStatus, errorMsg, now);
    }

    @Override
    public List<ClearanceTaskEntity> findByStatusAndRetryCountLessThan(Integer status, Integer maxRetry) {
        return clearanceTaskMapper.selectList(new QueryWrapper<ClearanceTaskEntity>()
                .eq("status", status)
                .lt("retry_count", maxRetry));
    }

    @Override
    public List<ClearanceTaskEntity> findByStatusAndUpdateTimeBefore(Integer status, LocalDateTime before) {
        return clearanceTaskMapper.selectList(new QueryWrapper<ClearanceTaskEntity>()
                .eq("status", status)
                .lt("update_time", before));
    }

    @Override
    public List<ClearanceTaskEntity> findByStatusAndShardIdAndUpdateTimeBefore(
            Integer status, int shardId, LocalDateTime before, int limit) {
        return clearanceTaskMapper.selectList(new QueryWrapper<ClearanceTaskEntity>()
                .eq("status", status)
                .eq("shard_id", shardId)
                .lt("update_time", before)
                .last("LIMIT " + limit));
    }

    @Override
    public long countByStatus(Integer status) {
        return clearanceTaskMapper.selectCount(new QueryWrapper<ClearanceTaskEntity>()
                .eq("status", status));
    }

    @Override
    public List<ClearanceTaskEntity> findFailedReadyForRetry(Integer status, Integer maxRetry, LocalDateTime now) {
        return clearanceTaskMapper.selectList(new QueryWrapper<ClearanceTaskEntity>()
                .eq("status", status)
                .lt("retry_count", maxRetry)
                .and(w -> w.isNull("next_retry_time").or().le("next_retry_time", now))
                .orderByAsc("create_time"));
    }

    @Override
    public List<ClearanceTaskEntity> findFailedReadyForRetryByShard(
            Integer status, Integer maxRetry, LocalDateTime now, int shardId, int limit) {
        return clearanceTaskMapper.selectList(new QueryWrapper<ClearanceTaskEntity>()
                .eq("status", status)
                .eq("shard_id", shardId)
                .lt("retry_count", maxRetry)
                .and(w -> w.isNull("next_retry_time").or().le("next_retry_time", now))
                .orderByAsc("create_time")
                .last("LIMIT " + limit));
    }
}
