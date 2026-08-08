package com.payment.domain.mapper;

import com.baomidou.dynamic.datasource.annotation.DS;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.payment.domain.datasource.DataSourceNames;
import com.payment.domain.entity.AccountFlowEntity;
import org.apache.ibatis.annotations.Mapper;

/**
 * 账户流水表 Mapper，映射 account_flow 表。
 * 数据源：data 分片库。
 */
@Mapper
@DS(DataSourceNames.DATA)
public interface AccountFlowMapper extends BaseMapper<AccountFlowEntity> {
}
