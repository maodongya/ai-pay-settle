package com.payment.domain.repository;

import com.payment.domain.entity.SplitDetailEntity;

import java.util.List;

/**
 * 分账明细仓储接口
 */
public interface SplitDetailRepository {

    SplitDetailEntity save(SplitDetailEntity entity);

    List<SplitDetailEntity> saveAll(List<SplitDetailEntity> entities);

    boolean existsByBillNo(String billNo);

    List<SplitDetailEntity> findByBillNo(String billNo);
}
