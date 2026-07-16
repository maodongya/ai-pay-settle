package com.payment.domain.repository.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.payment.domain.entity.FeeCalcResultEntity;
import com.payment.domain.mapper.FeeCalcResultMapper;
import com.payment.domain.repository.FeeCalcResultRepository;
import com.payment.domain.service.ShardRouteService;
import com.payment.domain.support.MapperHelper;
import com.payment.domain.support.ShardQueryHelper;
import org.springframework.stereotype.Repository;

import java.util.Optional;

/**
 * {@link FeeCalcResultRepository} 的 MyBatis-Plus 实现。
 */
@Repository
public class FeeCalcResultRepositoryImpl implements FeeCalcResultRepository {

    private final FeeCalcResultMapper feeCalcResultMapper;
    private final ShardRouteService shardRouteService;

    public FeeCalcResultRepositoryImpl(FeeCalcResultMapper feeCalcResultMapper,
                                       ShardRouteService shardRouteService) {
        this.feeCalcResultMapper = feeCalcResultMapper;
        this.shardRouteService = shardRouteService;
    }

    @Override
    public FeeCalcResultEntity save(FeeCalcResultEntity entity) {
        return MapperHelper.save(feeCalcResultMapper, entity);
    }

    /** 通过 bill_route 补全 merchant_id 精准路由 */
    @Override
    public Optional<FeeCalcResultEntity> findByBillNo(String billNo) {
        QueryWrapper<FeeCalcResultEntity> wrapper = new QueryWrapper<>();
        ShardQueryHelper.byBillNo(wrapper, billNo, shardRouteService);
        return Optional.ofNullable(feeCalcResultMapper.selectOne(wrapper));
    }
}
