package com.payment.domain.mapper;

import com.baomidou.dynamic.datasource.annotation.DS;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.payment.domain.datasource.DataSourceNames;
import com.payment.domain.entity.AlertRecordEntity;
import org.apache.ibatis.annotations.Mapper;

@Mapper
@DS(DataSourceNames.CONFIG)
public interface AlertRecordMapper extends BaseMapper<AlertRecordEntity> {
}
