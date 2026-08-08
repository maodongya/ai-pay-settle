package com.payment.domain.repository.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.payment.common.cache.CacheNames;
import com.payment.domain.entity.FeeShareRuleEntity;
import com.payment.domain.mapper.FeeShareRuleMapper;
import com.payment.domain.repository.FeeShareRuleRepository;
import com.payment.domain.support.MapperHelper;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * {@link FeeShareRuleRepository} 的 MyBatis-Plus 实现。
 */
@Repository
public class FeeShareRuleRepositoryImpl implements FeeShareRuleRepository {

    private final FeeShareRuleMapper feeShareRuleMapper;

    public FeeShareRuleRepositoryImpl(FeeShareRuleMapper feeShareRuleMapper) {
        this.feeShareRuleMapper = feeShareRuleMapper;
    }

    /** 写入时清空规则缓存 */
    @Override
    @CacheEvict(cacheNames = CacheNames.FEE_RULES, allEntries = true)
    public FeeShareRuleEntity save(FeeShareRuleEntity entity) {
        return MapperHelper.save(feeShareRuleMapper, entity);
    }

    @Override
    @Cacheable(cacheNames = CacheNames.FEE_RULES, key = "'all'")
    public List<FeeShareRuleEntity> findAll() {
        return feeShareRuleMapper.selectList(new QueryWrapper<>());
    }

    @Override
    @Cacheable(cacheNames = CacheNames.FEE_RULES, key = "'tt:' + #targetType + ':st:' + #status")
    public List<FeeShareRuleEntity> findByTargetTypeAndStatus(Integer targetType, Integer status) {
        return feeShareRuleMapper.selectList(new QueryWrapper<FeeShareRuleEntity>()
                .eq("target_type", targetType)
                .eq("status", status));
    }
}
