package com.payment.domain.mapper;

import com.baomidou.dynamic.datasource.annotation.DS;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.payment.domain.datasource.DataSourceNames;
import com.payment.domain.entity.AgentMerchantRelationEntity;
import org.apache.ibatis.annotations.Mapper;

/**
 * 代理商商户关系表 Mapper，映射 agent_merchant_relation 表。
 * 数据源：config 配置库。
 */
@Mapper
@DS(DataSourceNames.CONFIG)
public interface AgentMerchantRelationMapper extends BaseMapper<AgentMerchantRelationEntity> {
}
