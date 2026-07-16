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

    Optional<TradeBillEntity> findByBillNoAndMerchantId(String billNo, Long merchantId);

    int updateStatusByBillNoAndMerchantId(String billNo, Long merchantId,
                                          Integer expectedStatus, Integer newStatus);

    List<TradeBillEntity> findByStatusAndOriginBillNo(Integer status, String originBillNo);

    List<TradeBillEntity> findByStatus(Integer status);

    /** 按分片查询最近单据（路由补偿 Job） */
    List<TradeBillEntity> findRecentByShardId(int shardId, int limit);
}
