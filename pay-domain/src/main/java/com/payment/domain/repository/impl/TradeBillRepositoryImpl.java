package com.payment.domain.repository.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.payment.domain.entity.TradeBillEntity;
import com.payment.domain.mapper.TradeBillMapper;
import com.payment.domain.repository.TradeBillRepository;
import com.payment.domain.service.ShardRouteService;
import com.payment.domain.support.MapperHelper;
import com.payment.domain.support.ShardQueryHelper;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * {@link TradeBillRepository} 的 MyBatis-Plus 实现。
 */
@Repository
public class TradeBillRepositoryImpl implements TradeBillRepository {

    private final TradeBillMapper tradeBillMapper;
    private final ShardRouteService shardRouteService;

    public TradeBillRepositoryImpl(TradeBillMapper tradeBillMapper, ShardRouteService shardRouteService) {
        this.tradeBillMapper = tradeBillMapper;
        this.shardRouteService = shardRouteService;
    }

    @Override
    public TradeBillEntity save(TradeBillEntity entity) {
        return MapperHelper.save(tradeBillMapper, entity);
    }

    /** 通过 bill_route 补全 merchant_id 精准路由 */
    @Override
    public Optional<TradeBillEntity> findByBillNo(String billNo) {
        QueryWrapper<TradeBillEntity> wrapper = new QueryWrapper<>();
        ShardQueryHelper.byBillNo(wrapper, billNo, shardRouteService);
        return Optional.ofNullable(tradeBillMapper.selectOne(wrapper));
    }

    @Override
    public Optional<TradeBillEntity> findByBillNoAndMerchantId(String billNo, Long merchantId) {
        return Optional.ofNullable(tradeBillMapper.selectOne(new QueryWrapper<TradeBillEntity>()
                .eq("bill_no", billNo)
                .eq("merchant_id", merchantId)));
    }

    @Override
    public int updateStatusByBillNoAndMerchantId(String billNo, Long merchantId,
                                               Integer expectedStatus, Integer newStatus) {
        return tradeBillMapper.updateStatusByBillNoAndMerchantId(
                billNo, merchantId, expectedStatus, newStatus, LocalDateTime.now());
    }

    @Override
    public List<TradeBillEntity> findByStatusAndOriginBillNo(Integer status, String originBillNo) {
        QueryWrapper<TradeBillEntity> wrapper = new QueryWrapper<TradeBillEntity>()
                .eq("status", status);
        ShardQueryHelper.byBillNo(wrapper, originBillNo, shardRouteService);
        return tradeBillMapper.selectList(wrapper);
    }

    @Override
    public List<TradeBillEntity> findByStatus(Integer status) {
        return tradeBillMapper.selectList(new QueryWrapper<TradeBillEntity>().eq("status", status));
    }

    /** 按 merchant_id 取模过滤分片 */
    @Override
    public List<TradeBillEntity> findRecentByShardId(int shardId, int limit) {
        QueryWrapper<TradeBillEntity> wrapper = new QueryWrapper<>();
        ShardQueryHelper.eqShardId(wrapper, shardId);
        return tradeBillMapper.selectList(wrapper
                .orderByDesc("create_time")
                .last("LIMIT " + limit));
    }
}
