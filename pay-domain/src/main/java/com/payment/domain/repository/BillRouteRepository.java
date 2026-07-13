package com.payment.domain.repository;

import com.payment.domain.entity.BillRouteEntity;

import java.util.Optional;

public interface BillRouteRepository {

    BillRouteEntity save(BillRouteEntity entity);

    Optional<BillRouteEntity> findByBillNo(String billNo);
}
