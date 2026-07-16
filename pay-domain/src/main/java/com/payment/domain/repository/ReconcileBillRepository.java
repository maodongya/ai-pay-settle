package com.payment.domain.repository;

import com.payment.domain.entity.ReconcileBillEntity;

import java.time.LocalDate;
import java.util.Optional;

/**
 * 对账单仓储接口
 */
public interface ReconcileBillRepository {

    /** 保存或更新对账单 */
    ReconcileBillEntity save(ReconcileBillEntity entity);

    /** 按商户 ID 与账单日期查询对账单 */
    Optional<ReconcileBillEntity> findByMerchantIdAndBillDate(Long merchantId, LocalDate billDate);
}
