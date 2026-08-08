package com.payment.domain.mapper;

import com.baomidou.dynamic.datasource.annotation.DS;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.payment.domain.datasource.DataSourceNames;
import com.payment.domain.entity.ReconcileBillEntity;
import org.apache.ibatis.annotations.Mapper;

/**
 * 对账单表 Mapper，映射 reconcile_bill 表。
 * 数据源：data 分片库。
 */
@Mapper
@DS(DataSourceNames.DATA)
public interface ReconcileBillMapper extends BaseMapper<ReconcileBillEntity> {
}
