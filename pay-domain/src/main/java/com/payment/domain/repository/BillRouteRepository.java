package com.payment.domain.repository;

import com.payment.domain.entity.BillRouteEntity;

import java.util.Optional;

/**
 * 账单路由仓储接口
 */
public interface BillRouteRepository {

    /** 保存或更新账单路由 */
    BillRouteEntity save(BillRouteEntity entity);

    /** 按账单号查询路由 */
    Optional<BillRouteEntity> findByBillNo(String billNo);
}
