package com.payment.domain.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.payment.domain.entity.MerchantProfileEntity;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface MerchantProfileMapper extends BaseMapper<MerchantProfileEntity> {
}
