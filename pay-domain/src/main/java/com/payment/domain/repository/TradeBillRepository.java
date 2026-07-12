package com.payment.domain.repository;

import com.payment.domain.entity.TradeBillEntity;

import java.util.List;
import java.util.Optional;

/**
 * 交易账单仓储接口
 */
public interface TradeBillRepository {

    TradeBillEntity save(TradeBillEntity entity);

    Optional<TradeBillEntity> findByBillNo(String billNo);

    List<TradeBillEntity> findByStatusAndOriginBillNo(Integer status, String originBillNo);

    List<TradeBillEntity> findByStatus(Integer status);
}
