package com.payment.domain.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.dynamic.datasource.annotation.DS;
import com.payment.domain.datasource.DataSourceNames;
import com.payment.domain.entity.SettleRouteEntity;
import org.apache.ibatis.annotations.Mapper;

/**
 * 结算单路由表 Mapper，映射 settle_route 表。
 * 数据源：config 配置库。
 */
@Mapper
@DS(DataSourceNames.CONFIG)
public interface SettleRouteMapper extends BaseMapper<SettleRouteEntity> {
}
