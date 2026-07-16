package com.payment.domain.mapper;

import com.baomidou.dynamic.datasource.annotation.DS;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.payment.domain.datasource.DataSourceNames;
import com.payment.domain.entity.SettlementOrderEntity;
import org.apache.ibatis.annotations.Mapper;

/**
 * 结算单表 Mapper，映射 settlement_order 表。
 * 数据源：data 分片库。
 */
@Mapper
@DS(DataSourceNames.DATA)
public interface SettlementOrderMapper extends BaseMapper<SettlementOrderEntity> {
}
