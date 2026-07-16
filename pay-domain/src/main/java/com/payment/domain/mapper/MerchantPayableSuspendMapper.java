package com.payment.domain.mapper;

import com.baomidou.dynamic.datasource.annotation.DS;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.payment.domain.datasource.DataSourceNames;
import com.payment.domain.entity.MerchantPayableSuspendEntity;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

import java.math.BigDecimal;

/**
 * 商户应付挂账表 Mapper，映射 merchant_payable_suspend 表。
 * 数据源：data 分片库。
 */
@Mapper
@DS(DataSourceNames.DATA)
public interface MerchantPayableSuspendMapper extends BaseMapper<MerchantPayableSuspendEntity> {

    /** 结算抵扣挂账金额，足额时自动置为已结清 */
    @Update("UPDATE merchant_payable_suspend SET "
            + "settled_amount = settled_amount + #{deduct}, "
            + "status = CASE WHEN settled_amount + #{deduct} >= suspend_amount THEN 1 ELSE status END "
            + "WHERE id = #{id} AND merchant_id = #{merchantId} AND status = 0 "
            + "AND suspend_amount - settled_amount >= #{deduct}")
    int applySettlementOffset(@Param("id") Long id,
                              @Param("merchantId") Long merchantId,
                              @Param("deduct") BigDecimal deduct);
}
