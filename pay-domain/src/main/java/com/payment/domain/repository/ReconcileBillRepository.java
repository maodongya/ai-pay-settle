package com.payment.domain.repository;

import com.payment.domain.entity.ReconcileBillEntity;

import java.time.LocalDate;
import java.util.Optional;

/**
 * 对账单仓储接口
 */
public interface ReconcileBillRepository {

    ReconcileBillEntity save(ReconcileBillEntity entity);

    Optional<ReconcileBillEntity> findByMerchantIdAndBillDate(Long merchantId, LocalDate billDate);
}
