package com.payment.domain.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.LocalDateTime;

@TableName("settle_route")
public class SettleRouteEntity {

    @TableId(value = "settle_no", type = IdType.INPUT)
    public String settleNo;

    @TableField("merchant_id")
    public Long merchantId;

    @TableField(value = "create_time", fill = FieldFill.INSERT)
    public LocalDateTime createTime;
}
