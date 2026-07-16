package com.payment.domain.repository;

import com.payment.domain.entity.MerchantProfileEntity;

import java.util.Optional;

/**
 * 商户档案仓储接口
 */
public interface MerchantProfileRepository {

    /** 按商户 ID 查询档案 */
    Optional<MerchantProfileEntity> findById(Long merchantId);
}
