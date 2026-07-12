package com.payment.domain.repository;

import com.payment.domain.entity.MerchantProfileEntity;

import java.util.Optional;

/**
 * 商户档案仓储接口
 */
public interface MerchantProfileRepository {

    Optional<MerchantProfileEntity> findById(Long merchantId);
}
