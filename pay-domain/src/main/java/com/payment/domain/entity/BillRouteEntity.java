package com.payment.domain.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.LocalDateTime;

/**
 * 账单路由实体
 * 对应数据库表 bill_route
 */
@TableName("bill_route")
public class BillRouteEntity {

    /** 账单号（主键） */
    @TableId(value = "bill_no", type = IdType.INPUT)
    public String billNo;

    /** 商户 ID */
    @TableField("merchant_id")
    public Long merchantId;

    /** 账单类型 */
    @TableField("bill_type")
    public Integer billType;

    /** 创建时间 */
    @TableField(value = "create_time", fill = FieldFill.INSERT)
    public LocalDateTime createTime;
}
