package com.payment.domain.repository.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.payment.domain.entity.SplitDetailEntity;
import com.payment.domain.mapper.SplitDetailMapper;
import com.payment.domain.repository.SplitDetailRepository;
import com.payment.domain.service.ShardRouteService;
import com.payment.domain.support.MapperHelper;
import com.payment.domain.support.ShardQueryHelper;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public class SplitDetailRepositoryImpl implements SplitDetailRepository {

    private final SplitDetailMapper splitDetailMapper;
    private final ShardRouteService shardRouteService;

    public SplitDetailRepositoryImpl(SplitDetailMapper splitDetailMapper,
                                     ShardRouteService shardRouteService) {
        this.splitDetailMapper = splitDetailMapper;
        this.shardRouteService = shardRouteService;
    }

    @Override
    public SplitDetailEntity save(SplitDetailEntity entity) {
        return MapperHelper.save(splitDetailMapper, entity);
    }

    @Override
    public List<SplitDetailEntity> saveAll(List<SplitDetailEntity> entities) {
        return MapperHelper.saveAll(splitDetailMapper, entities);
    }

    @Override
    public boolean existsByBillNo(String billNo) {
        QueryWrapper<SplitDetailEntity> wrapper = new QueryWrapper<>();
        ShardQueryHelper.byBillNo(wrapper, billNo, shardRouteService);
        return splitDetailMapper.selectCount(wrapper) > 0;
    }

    @Override
    public List<SplitDetailEntity> findByBillNo(String billNo) {
        QueryWrapper<SplitDetailEntity> wrapper = new QueryWrapper<>();
        ShardQueryHelper.byBillNo(wrapper, billNo, shardRouteService);
        return splitDetailMapper.selectList(wrapper);
    }
}
