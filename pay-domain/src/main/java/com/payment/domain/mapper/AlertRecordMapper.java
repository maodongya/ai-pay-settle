package com.payment.domain.mapper;

import com.baomidou.dynamic.datasource.annotation.DS;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.payment.domain.datasource.DataSourceNames;
import com.payment.domain.entity.AlertRecordEntity;
import org.apache.ibatis.annotations.Mapper;

/**
 * 告警记录表 Mapper，映射 alert_record 表。
 * 数据源：config 配置库。
 */
@Mapper
@DS(DataSourceNames.CONFIG)
public interface AlertRecordMapper extends BaseMapper<AlertRecordEntity> {
}
