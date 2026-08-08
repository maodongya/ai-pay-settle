package com.payment.domain.repository;

import com.payment.domain.entity.TradeBillEntity;

import java.util.List;
import java.util.Optional;

/**
 * 交易账单仓储接口
 */
public interface TradeBillRepository {

    /** 保存或更新交易账单 */
    TradeBillEntity save(TradeBillEntity entity);

    /** 按账单号查询（带分片路由） */
    Optional<TradeBillEntity> findByBillNo(String billNo);

    /** 按账单号与商户 ID 查询 */
    Optional<TradeBillEntity> findByBillNoAndMerchantId(String billNo, Long merchantId);

    /** 按账单号与商户 ID 乐观更新状态 */
    int updateStatusByBillNoAndMerchantId(String billNo, Long merchantId,
                                          Integer expectedStatus, Integer newStatus);

    /** 按状态与原账单号查询 */
    List<TradeBillEntity> findByStatusAndOriginBillNo(Integer status, String originBillNo);

    /** 按状态查询账单 */
    List<TradeBillEntity> findByStatus(Integer status);

    /** 按分片查询最近单据（路由补偿 Job） */
    List<TradeBillEntity> findRecentByShardId(int shardId, int limit);
}
