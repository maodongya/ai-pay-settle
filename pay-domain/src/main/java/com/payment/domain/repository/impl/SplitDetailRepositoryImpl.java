package com.payment.domain.repository.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.payment.domain.entity.SplitDetailEntity;
import com.payment.domain.mapper.SplitDetailMapper;
import com.payment.domain.repository.SplitDetailRepository;
import com.payment.domain.support.MapperHelper;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public class SplitDetailRepositoryImpl implements SplitDetailRepository {

    private final SplitDetailMapper splitDetailMapper;

    public SplitDetailRepositoryImpl(SplitDetailMapper splitDetailMapper) {
        this.splitDetailMapper = splitDetailMapper;
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
        return splitDetailMapper.selectCount(new QueryWrapper<SplitDetailEntity>().eq("bill_no", billNo)) > 0;
    }

    @Override
    public List<SplitDetailEntity> findByBillNo(String billNo) {
        return splitDetailMapper.selectList(new QueryWrapper<SplitDetailEntity>().eq("bill_no", billNo));
    }
}
