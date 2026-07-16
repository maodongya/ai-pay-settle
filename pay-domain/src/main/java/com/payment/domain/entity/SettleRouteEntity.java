package com.payment.domain.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.LocalDateTime;

/**
 * 结算单路由实体
 * 对应数据库表 settle_route
 */
@TableName("settle_route")
public class SettleRouteEntity {

    /** 结算单号（主键） */
    @TableId(value = "settle_no", type = IdType.INPUT)
    public String settleNo;

    /** 商户 ID */
    @TableField("merchant_id")
    public Long merchantId;

    /** 创建时间 */
    @TableField(value = "create_time", fill = FieldFill.INSERT)
    public LocalDateTime createTime;
}
