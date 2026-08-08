package com.payment.domain.mapper;

import com.baomidou.dynamic.datasource.annotation.DS;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.payment.domain.datasource.DataSourceNames;
import com.payment.domain.entity.SettlementOrderEntity;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

import java.time.LocalDateTime;

/**
 * 结算单表 Mapper，映射 settlement_order 表。
 * 数据源：data 分片库。
 */
@Mapper
@DS(DataSourceNames.DATA)
public interface SettlementOrderMapper extends BaseMapper<SettlementOrderEntity> {

    /** 支付回调单 SQL 更新结算单（PAYING → SUCCESS/FAILED） */
    @Update("UPDATE settlement_order SET status = #{newStatus}, channel_trade_no = #{channelTradeNo}, "
            + "fail_reason = #{failReason}, update_time = #{now} "
            + "WHERE settle_no = #{settleNo} AND merchant_id = #{merchantId} AND status = #{expectedStatus}")
    int updatePaymentResult(@Param("settleNo") String settleNo,
                            @Param("merchantId") Long merchantId,
                            @Param("expectedStatus") Integer expectedStatus,
                            @Param("newStatus") Integer newStatus,
                            @Param("channelTradeNo") String channelTradeNo,
                            @Param("failReason") String failReason,
                            @Param("now") LocalDateTime now);
}
