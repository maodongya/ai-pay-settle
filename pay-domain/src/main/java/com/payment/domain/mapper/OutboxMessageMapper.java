package com.payment.domain.mapper;

import com.baomidou.dynamic.datasource.annotation.DS;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.payment.domain.datasource.DataSourceNames;
import com.payment.domain.entity.OutboxMessageEntity;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

@Mapper
@DS(DataSourceNames.DATA)
public interface OutboxMessageMapper extends BaseMapper<OutboxMessageEntity> {

    @Update("UPDATE outbox_message SET status = 1 "
            + "WHERE id = #{id} AND merchant_id = #{merchantId} AND status = 0")
    int markSentByIdAndMerchantId(@Param("id") Long id, @Param("merchantId") Long merchantId);
}
