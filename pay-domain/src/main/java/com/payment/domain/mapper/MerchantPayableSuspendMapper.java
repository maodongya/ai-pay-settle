package com.payment.domain.mapper;

import com.baomidou.dynamic.datasource.annotation.DS;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.payment.domain.datasource.DataSourceNames;
import com.payment.domain.entity.MerchantPayableSuspendEntity;
import org.apache.ibatis.annotations.Mapper;

@Mapper
@DS(DataSourceNames.DATA)
public interface MerchantPayableSuspendMapper extends BaseMapper<MerchantPayableSuspendEntity> {
}
