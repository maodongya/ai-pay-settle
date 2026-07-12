package com.payment.domain.repository;

import com.payment.domain.entity.FeeCalcResultEntity;

import java.util.Optional;

/**
 * 费用计算结果仓储接口
 */
public interface FeeCalcResultRepository {

    FeeCalcResultEntity save(FeeCalcResultEntity entity);

    Optional<FeeCalcResultEntity> findByBillNo(String billNo);
}
