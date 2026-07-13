package com.payment.domain.repository;

import com.payment.domain.entity.SettleRouteEntity;

import java.util.Optional;

public interface SettleRouteRepository {

    SettleRouteEntity save(SettleRouteEntity entity);

    Optional<SettleRouteEntity> findBySettleNo(String settleNo);
}
