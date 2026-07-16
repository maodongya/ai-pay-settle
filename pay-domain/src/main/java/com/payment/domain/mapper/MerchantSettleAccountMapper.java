package com.payment.domain.mapper;

import com.baomidou.dynamic.datasource.annotation.DS;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.payment.domain.datasource.DataSourceNames;
import com.payment.domain.entity.MerchantSettleAccountEntity;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

import java.math.BigDecimal;
import java.time.LocalDateTime;

@Mapper
@DS(DataSourceNames.DATA)
public interface MerchantSettleAccountMapper extends BaseMapper<MerchantSettleAccountEntity> {

    @Update("UPDATE merchant_settle_account SET "
            + "wait_balance = wait_balance + #{waitDelta}, "
            + "frozen_balance = frozen_balance + #{frozenDelta}, "
            + "version = version + 1, "
            + "update_time = #{now} "
            + "WHERE merchant_id = #{merchantId} "
            + "AND version = #{version} "
            + "AND wait_balance + #{waitDelta} >= 0 "
            + "AND frozen_balance + #{frozenDelta} >= 0")
    int updateBalanceCas(@Param("merchantId") Long merchantId,
                         @Param("waitDelta") BigDecimal waitDelta,
                         @Param("frozenDelta") BigDecimal frozenDelta,
                         @Param("version") Integer version,
                         @Param("now") LocalDateTime now);

    @Update("UPDATE merchant_settle_account SET "
            + "frozen_balance = frozen_balance + #{frozenDelta}, "
            + "version = version + 1, "
            + "update_time = #{now} "
            + "WHERE merchant_id = #{merchantId} "
            + "AND version = #{version} "
            + "AND frozen_balance + #{frozenDelta} >= 0")
    int updateFrozenCas(@Param("merchantId") Long merchantId,
                        @Param("frozenDelta") BigDecimal frozenDelta,
                        @Param("version") Integer version,
                        @Param("now") LocalDateTime now);
}
