package com.payment.domain.mapper;

import com.baomidou.dynamic.datasource.annotation.DS;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.payment.domain.datasource.DataSourceNames;
import com.payment.domain.entity.OutboxMessageEntity;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

/**
 * 发件箱消息表 Mapper，映射 outbox_message 表。
 * 数据源：data 分片库。
 */
@Mapper
@DS(DataSourceNames.DATA)
public interface OutboxMessageMapper extends BaseMapper<OutboxMessageEntity> {

    /** 按主键与商户 ID 标记消息已发送 */
    @Update("UPDATE outbox_message SET status = 1 "
            + "WHERE id = #{id} AND merchant_id = #{merchantId} AND status = 0")
    int markSentByIdAndMerchantId(@Param("id") Long id, @Param("merchantId") Long merchantId);
}
