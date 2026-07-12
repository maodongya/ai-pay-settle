package com.payment.domain.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.payment.domain.entity.OutboxMessageEntity;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface OutboxMessageMapper extends BaseMapper<OutboxMessageEntity> {
}
