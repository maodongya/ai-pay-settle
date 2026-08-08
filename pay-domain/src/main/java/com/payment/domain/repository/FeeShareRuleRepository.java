package com.payment.domain.repository;

import com.payment.domain.entity.FeeShareRuleEntity;

import java.util.List;

/**
 * 费用分摊规则仓储接口
 */
public interface FeeShareRuleRepository {

    /** 保存或更新分摊规则 */
    FeeShareRuleEntity save(FeeShareRuleEntity entity);

    /** 查询全部规则 */
    List<FeeShareRuleEntity> findAll();

    /** 按目标类型与状态查询规则 */
    List<FeeShareRuleEntity> findByTargetTypeAndStatus(Integer targetType, Integer status);
}
