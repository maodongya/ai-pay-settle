package com.payment.domain.repository;

import com.payment.domain.entity.AccountFlowEntity;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 账户流水仓储接口
 */
public interface AccountFlowRepository {

    /** 保存或更新流水记录 */
    AccountFlowEntity save(AccountFlowEntity entity);

    /** 按账单号与操作类型判断流水是否已存在 */
    boolean existsByBillNoAndOpType(String billNo, Integer opType);

    /** 按结算单号与操作类型判断流水是否已存在 */
    boolean existsBySettleNoAndOpType(String settleNo, Integer opType);

    /** 按商户 ID 与时间范围查询流水 */
    List<AccountFlowEntity> findByMerchantIdAndCreateTimeBetween(
            Long merchantId, LocalDateTime start, LocalDateTime end);
}
