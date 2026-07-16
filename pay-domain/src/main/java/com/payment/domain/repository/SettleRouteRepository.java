package com.payment.domain.repository;

import com.payment.domain.entity.SettleRouteEntity;

import java.util.Optional;

/**
 * 结算单路由仓储接口
 */
public interface SettleRouteRepository {

    /** 保存或更新结算单路由 */
    SettleRouteEntity save(SettleRouteEntity entity);

    /** 按结算单号查询路由 */
    Optional<SettleRouteEntity> findBySettleNo(String settleNo);
}
