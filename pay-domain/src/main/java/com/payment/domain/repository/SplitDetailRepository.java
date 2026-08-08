package com.payment.domain.repository;

import com.payment.domain.entity.SplitDetailEntity;

import java.util.List;

/**
 * 分账明细仓储接口
 */
public interface SplitDetailRepository {

    /** 保存或更新单条分账明细 */
    SplitDetailEntity save(SplitDetailEntity entity);

    /** 批量保存分账明细（新记录走批量 INSERT） */
    List<SplitDetailEntity> saveAll(List<SplitDetailEntity> entities);

    /** 按账单号判断分账明细是否已存在 */
    boolean existsByBillNo(String billNo);

    /** 按账单号查询分账明细列表 */
    List<SplitDetailEntity> findByBillNo(String billNo);
}
