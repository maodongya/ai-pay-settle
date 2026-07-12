package com.payment.domain.repository.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.payment.domain.entity.FeeCalcResultEntity;
import com.payment.domain.mapper.FeeCalcResultMapper;
import com.payment.domain.repository.FeeCalcResultRepository;
import com.payment.domain.support.MapperHelper;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public class FeeCalcResultRepositoryImpl implements FeeCalcResultRepository {

    private final FeeCalcResultMapper feeCalcResultMapper;

    public FeeCalcResultRepositoryImpl(FeeCalcResultMapper feeCalcResultMapper) {
        this.feeCalcResultMapper = feeCalcResultMapper;
    }

    @Override
    public FeeCalcResultEntity save(FeeCalcResultEntity entity) {
        return MapperHelper.save(feeCalcResultMapper, entity);
    }

    @Override
    public Optional<FeeCalcResultEntity> findByBillNo(String billNo) {
        return Optional.ofNullable(feeCalcResultMapper.selectOne(
                new QueryWrapper<FeeCalcResultEntity>().eq("bill_no", billNo)));
    }
}
