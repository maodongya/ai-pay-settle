package com.payment.domain.mapper;

import com.baomidou.dynamic.datasource.annotation.DS;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.payment.domain.datasource.DataSourceNames;
import com.payment.domain.entity.TradeBillEntity;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

import java.time.LocalDateTime;

/**
 * 交易账单表 Mapper，映射 trade_bill 表。
 * 数据源：data 分片库。
 */
@Mapper
@DS(DataSourceNames.DATA)
public interface TradeBillMapper extends BaseMapper<TradeBillEntity> {

    /** 按账单号与商户 ID 乐观更新状态 */
    @Update("UPDATE trade_bill SET status = #{newStatus}, update_time = #{now} "
            + "WHERE bill_no = #{billNo} AND merchant_id = #{merchantId} AND status = #{expectedStatus}")
    int updateStatusByBillNoAndMerchantId(@Param("billNo") String billNo,
                                          @Param("merchantId") Long merchantId,
                                          @Param("expectedStatus") Integer expectedStatus,
                                          @Param("newStatus") Integer newStatus,
                                          @Param("now") LocalDateTime now);
}
