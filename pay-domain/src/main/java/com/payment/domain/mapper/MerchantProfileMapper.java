package com.payment.domain.mapper;

import com.baomidou.dynamic.datasource.annotation.DS;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.payment.domain.datasource.DataSourceNames;
import com.payment.domain.entity.MerchantProfileEntity;
import org.apache.ibatis.annotations.Mapper;

/**
 * 商户档案表 Mapper，映射 merchant_profile 表。
 * 数据源：config 配置库。
 */
@Mapper
@DS(DataSourceNames.CONFIG)
public interface MerchantProfileMapper extends BaseMapper<MerchantProfileEntity> {
}
