package com.payment.domain.repository;

import com.payment.domain.entity.AgentMerchantRelationEntity;

import java.util.Optional;

/**
 * 代理商与商户关系仓储接口
 */
public interface AgentMerchantRelationRepository {

    /** 按商户 ID 查询最新代理商关系 */
    Optional<AgentMerchantRelationEntity> findFirstByMerchantIdOrderByRelIdDesc(Long merchantId);
}
