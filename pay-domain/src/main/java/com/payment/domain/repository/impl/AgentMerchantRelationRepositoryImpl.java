package com.payment.domain.repository.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.payment.common.cache.CacheNames;
import com.payment.domain.entity.AgentMerchantRelationEntity;
import com.payment.domain.mapper.AgentMerchantRelationMapper;
import com.payment.domain.repository.AgentMerchantRelationRepository;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Repository;

import java.util.Optional;

/**
 * {@link AgentMerchantRelationRepository} 的 MyBatis-Plus 实现。
 */
@Repository
public class AgentMerchantRelationRepositoryImpl implements AgentMerchantRelationRepository {

    private final AgentMerchantRelationMapper agentMerchantRelationMapper;

    public AgentMerchantRelationRepositoryImpl(AgentMerchantRelationMapper agentMerchantRelationMapper) {
        this.agentMerchantRelationMapper = agentMerchantRelationMapper;
    }

    /** 结果缓存，减少 config 库查询 */
    @Override
    @Cacheable(cacheNames = CacheNames.AGENT_RELATION, key = "#merchantId", unless = "#result == null")
    public Optional<AgentMerchantRelationEntity> findFirstByMerchantIdOrderByRelIdDesc(Long merchantId) {
        return Optional.ofNullable(agentMerchantRelationMapper.selectOne(
                new QueryWrapper<AgentMerchantRelationEntity>()
                        .eq("merchant_id", merchantId)
                        .orderByDesc("rel_id")
                        .last("LIMIT 1")));
    }
}
