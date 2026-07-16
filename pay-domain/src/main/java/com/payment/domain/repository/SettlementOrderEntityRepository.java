package com.payment.domain.repository;

import com.payment.domain.entity.SettlementOrderEntity;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * 结算单仓储接口
 */
public interface SettlementOrderEntityRepository {

    /** 保存或更新结算单 */
    SettlementOrderEntity save(SettlementOrderEntity entity);

    /** 按结算单号查询 */
    Optional<SettlementOrderEntity> findBySettleNo(String settleNo);

    /** 按商户 ID 与状态查询结算单 */
    List<SettlementOrderEntity> findByMerchantIdAndStatus(Long merchantId, Integer status);

    /** 按状态查询结算单 */
    List<SettlementOrderEntity> findByStatus(Integer status);

    /** 按商户 ID、状态与更新时间范围查询 */
    List<SettlementOrderEntity> findByMerchantIdAndStatusAndUpdateTimeBetween(
            Long merchantId, Integer status, LocalDateTime start, LocalDateTime end);

    /** 按原结算单号判断是否存在 */
    boolean existsByOriginSettleNo(String originSettleNo);

    /** 按原结算单号判断是否存在非指定状态的记录 */
    boolean existsByOriginSettleNoAndStatusNot(String originSettleNo, Integer status);
}
