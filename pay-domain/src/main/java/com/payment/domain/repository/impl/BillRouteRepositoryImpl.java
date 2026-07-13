package com.payment.domain.repository.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.payment.domain.entity.BillRouteEntity;
import com.payment.domain.mapper.BillRouteMapper;
import com.payment.domain.repository.BillRouteRepository;
import com.payment.domain.support.MapperHelper;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public class BillRouteRepositoryImpl implements BillRouteRepository {

    private final BillRouteMapper billRouteMapper;

    public BillRouteRepositoryImpl(BillRouteMapper billRouteMapper) {
        this.billRouteMapper = billRouteMapper;
    }

    @Override
    public BillRouteEntity save(BillRouteEntity entity) {
        return MapperHelper.save(billRouteMapper, entity);
    }

    @Override
    public Optional<BillRouteEntity> findByBillNo(String billNo) {
        return Optional.ofNullable(billRouteMapper.selectById(billNo));
    }
}
