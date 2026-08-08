package com.payment.domain.repository;

import com.payment.domain.entity.FeeCalcResultEntity;

import java.util.Optional;

/**
 * 费用计算结果仓储接口
 */
public interface FeeCalcResultRepository {

    /** 保存或更新费用计算结果 */
    FeeCalcResultEntity save(FeeCalcResultEntity entity);

    /** 按账单号查询费用计算结果 */
    Optional<FeeCalcResultEntity> findByBillNo(String billNo);
}
