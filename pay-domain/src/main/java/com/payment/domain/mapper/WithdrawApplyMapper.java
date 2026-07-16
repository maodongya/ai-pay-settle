package com.payment.domain.mapper;

import com.baomidou.dynamic.datasource.annotation.DS;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.payment.domain.datasource.DataSourceNames;
import com.payment.domain.entity.WithdrawApplyEntity;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

@Mapper
@DS(DataSourceNames.DATA)
public interface WithdrawApplyMapper extends BaseMapper<WithdrawApplyEntity> {

    @Update("UPDATE withdraw_apply SET status = #{status} "
            + "WHERE settle_no = #{settleNo} AND merchant_id = #{merchantId}")
    int updateStatusBySettleNoAndMerchantId(@Param("settleNo") String settleNo,
                                            @Param("merchantId") Long merchantId,
                                            @Param("status") Integer status);
}
