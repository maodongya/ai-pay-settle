package com.payment.domain.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.LocalDateTime;

@TableName("bill_route")
public class BillRouteEntity {

    @TableId(value = "bill_no", type = IdType.INPUT)
    public String billNo;

    @TableField("merchant_id")
    public Long merchantId;

    @TableField("bill_type")
    public Integer billType;

    @TableField(value = "create_time", fill = FieldFill.INSERT)
    public LocalDateTime createTime;
}
