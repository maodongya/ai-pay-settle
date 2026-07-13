package com.payment.domain.mapper;

import com.baomidou.dynamic.datasource.annotation.DS;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.payment.domain.datasource.DataSourceNames;
import com.payment.domain.entity.WithdrawApplyEntity;
import org.apache.ibatis.annotations.Mapper;

@Mapper
@DS(DataSourceNames.DATA)
public interface WithdrawApplyMapper extends BaseMapper<WithdrawApplyEntity> {
}
