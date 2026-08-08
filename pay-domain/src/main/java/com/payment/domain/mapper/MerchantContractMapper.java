package com.payment.domain.mapper;

import com.baomidou.dynamic.datasource.annotation.DS;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.payment.domain.datasource.DataSourceNames;
import com.payment.domain.entity.MerchantContractEntity;
import org.apache.ibatis.annotations.Mapper;

/**
 * 商户合同表 Mapper，映射 merchant_contract 表。
 * 数据源：config 配置库。
 */
@Mapper
@DS(DataSourceNames.CONFIG)
public interface MerchantContractMapper extends BaseMapper<MerchantContractEntity> {
}
