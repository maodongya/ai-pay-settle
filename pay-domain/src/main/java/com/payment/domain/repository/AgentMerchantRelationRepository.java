package com.payment.domain.repository;

import com.payment.domain.entity.AgentMerchantRelationEntity;

import java.util.Optional;

/**
 * 代理商与商户关系仓储接口
 */
public interface AgentMerchantRelationRepository {

    Optional<AgentMerchantRelationEntity> findFirstByMerchantIdOrderByRelIdDesc(Long merchantId);
}
