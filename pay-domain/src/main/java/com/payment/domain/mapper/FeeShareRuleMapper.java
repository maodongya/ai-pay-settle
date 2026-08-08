package com.payment.domain.mapper;

import com.baomidou.dynamic.datasource.annotation.DS;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.payment.domain.datasource.DataSourceNames;
import com.payment.domain.entity.FeeShareRuleEntity;
import org.apache.ibatis.annotations.Mapper;

/**
 * 费用分摊规则表 Mapper，映射 fee_share_rule 表。
 * 数据源：config 配置库。
 */
@Mapper
@DS(DataSourceNames.CONFIG)
public interface FeeShareRuleMapper extends BaseMapper<FeeShareRuleEntity> {
}
