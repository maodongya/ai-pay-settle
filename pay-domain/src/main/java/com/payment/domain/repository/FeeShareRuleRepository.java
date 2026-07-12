package com.payment.domain.repository;

import com.payment.domain.entity.FeeShareRuleEntity;

import java.util.List;

/**
 * 费用分摊规则仓储接口
 */
public interface FeeShareRuleRepository {

    FeeShareRuleEntity save(FeeShareRuleEntity entity);

    List<FeeShareRuleEntity> findAll();

    List<FeeShareRuleEntity> findByTargetTypeAndStatus(Integer targetType, Integer status);
}
