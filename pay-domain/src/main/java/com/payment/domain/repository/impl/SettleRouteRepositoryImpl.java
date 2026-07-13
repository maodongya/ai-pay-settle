package com.payment.domain.repository.impl;

import com.payment.domain.entity.SettleRouteEntity;
import com.payment.domain.mapper.SettleRouteMapper;
import com.payment.domain.repository.SettleRouteRepository;
import com.payment.domain.support.MapperHelper;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public class SettleRouteRepositoryImpl implements SettleRouteRepository {

    private final SettleRouteMapper settleRouteMapper;

    public SettleRouteRepositoryImpl(SettleRouteMapper settleRouteMapper) {
        this.settleRouteMapper = settleRouteMapper;
    }

    @Override
    public SettleRouteEntity save(SettleRouteEntity entity) {
        return MapperHelper.save(settleRouteMapper, entity);
    }

    @Override
    public Optional<SettleRouteEntity> findBySettleNo(String settleNo) {
        return Optional.ofNullable(settleRouteMapper.selectById(settleNo));
    }
}
