package com.payment.domain.mapper;

import com.baomidou.dynamic.datasource.annotation.DS;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.payment.domain.datasource.DataSourceNames;
import com.payment.domain.entity.FeeCalcResultEntity;
import org.apache.ibatis.annotations.Mapper;

/**
 * 费用计算结果表 Mapper，映射 fee_calc_result 表。
 * 数据源：data 分片库。
 */
@Mapper
@DS(DataSourceNames.DATA)
public interface FeeCalcResultMapper extends BaseMapper<FeeCalcResultEntity> {
}
