package com.payment.domain.mapper;

import com.baomidou.dynamic.datasource.annotation.DS;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.payment.domain.datasource.DataSourceNames;
import com.payment.domain.entity.WithdrawApplyEntity;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

/**
 * 提现申请表 Mapper，映射 withdraw_apply 表。
 * 数据源：data 分片库。
 */
@Mapper
@DS(DataSourceNames.DATA)
public interface WithdrawApplyMapper extends BaseMapper<WithdrawApplyEntity> {

    /** 按结算单号与商户 ID 更新申请状态 */
    @Update("UPDATE withdraw_apply SET status = #{status} "
            + "WHERE settle_no = #{settleNo} AND merchant_id = #{merchantId}")
    int updateStatusBySettleNoAndMerchantId(@Param("settleNo") String settleNo,
                                            @Param("merchantId") Long merchantId,
                                            @Param("status") Integer status);
}
