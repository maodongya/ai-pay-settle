package com.payment.domain.repository;

import com.payment.domain.entity.MerchantContractEntity;

import java.util.Optional;

/**
 * 商户合同仓储接口
 */
public interface MerchantContractRepository {

    /** 按商户 ID 查询合同 */
    Optional<MerchantContractEntity> findByMerchantId(Long merchantId);
}
