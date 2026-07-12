package com.payment.domain.entity; // 实体包声明

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

/**
 * 商户档案实体
 * 对应数据库表 merchant_profile
 */
@TableName("merchant_profile") // 映射到 merchant_profile 表
public class MerchantProfileEntity {

    /** 商户 ID（主键） */
    @TableId(value = "merchant_id", type = IdType.INPUT) // 主键，非自增
    public Long merchantId;

    /** 商户名称 */
    @TableField("merchant_name") // 非空，最大长度 128
    public String merchantName;

    /** 商户状态 */
    public Integer status; // 非空
}
