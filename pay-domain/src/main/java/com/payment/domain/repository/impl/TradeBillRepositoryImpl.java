package com.payment.domain.repository.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.payment.domain.entity.TradeBillEntity;
import com.payment.domain.mapper.TradeBillMapper;
import com.payment.domain.repository.TradeBillRepository;
import com.payment.domain.service.ShardRouteService;
import com.payment.domain.support.MapperHelper;
import com.payment.domain.support.ShardQueryHelper;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

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

    @Override
    public Optional<TradeBillEntity> findByBillNo(String billNo) {
        QueryWrapper<TradeBillEntity> wrapper = new QueryWrapper<>();
        ShardQueryHelper.byBillNo(wrapper, billNo, shardRouteService);
        return Optional.ofNullable(tradeBillMapper.selectOne(wrapper));
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

    @Override
    public List<TradeBillEntity> findRecentByShardId(int shardId, int limit) {
        QueryWrapper<TradeBillEntity> wrapper = new QueryWrapper<>();
        ShardQueryHelper.eqShardId(wrapper, shardId);
        return tradeBillMapper.selectList(wrapper
                .orderByDesc("create_time")
                .last("LIMIT " + limit));
    }
}
