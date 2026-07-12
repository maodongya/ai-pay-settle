package com.payment.domain.repository.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.payment.domain.entity.AgentMerchantRelationEntity;
import com.payment.domain.mapper.AgentMerchantRelationMapper;
import com.payment.domain.repository.AgentMerchantRelationRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public class AgentMerchantRelationRepositoryImpl implements AgentMerchantRelationRepository {

    private final AgentMerchantRelationMapper agentMerchantRelationMapper;

    public AgentMerchantRelationRepositoryImpl(AgentMerchantRelationMapper agentMerchantRelationMapper) {
        this.agentMerchantRelationMapper = agentMerchantRelationMapper;
    }

    @Override
    public Optional<AgentMerchantRelationEntity> findFirstByMerchantIdOrderByRelIdDesc(Long merchantId) {
        return Optional.ofNullable(agentMerchantRelationMapper.selectOne(
                new QueryWrapper<AgentMerchantRelationEntity>()
                        .eq("merchant_id", merchantId)
                        .orderByDesc("rel_id")
                        .last("LIMIT 1")));
    }
}
