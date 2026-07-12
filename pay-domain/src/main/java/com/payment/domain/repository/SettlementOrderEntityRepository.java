package com.payment.domain.repository;

import com.payment.domain.entity.SettlementOrderEntity;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * 结算单仓储接口
 */
public interface SettlementOrderEntityRepository {

    SettlementOrderEntity save(SettlementOrderEntity entity);

    Optional<SettlementOrderEntity> findBySettleNo(String settleNo);

    List<SettlementOrderEntity> findByMerchantIdAndStatus(Long merchantId, Integer status);

    List<SettlementOrderEntity> findByStatus(Integer status);

    List<SettlementOrderEntity> findByMerchantIdAndStatusAndUpdateTimeBetween(
            Long merchantId, Integer status, LocalDateTime start, LocalDateTime end);

    boolean existsByOriginSettleNo(String originSettleNo);

    boolean existsByOriginSettleNoAndStatusNot(String originSettleNo, Integer status);
}
