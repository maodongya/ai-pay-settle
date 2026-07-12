package com.payment.domain.repository;

import com.payment.domain.entity.AccountFlowEntity;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 账户流水仓储接口
 */
public interface AccountFlowRepository {

    AccountFlowEntity save(AccountFlowEntity entity);

    boolean existsByBillNoAndOpType(String billNo, Integer opType);

    List<AccountFlowEntity> findByMerchantIdAndCreateTimeBetween(
            Long merchantId, LocalDateTime start, LocalDateTime end);
}
