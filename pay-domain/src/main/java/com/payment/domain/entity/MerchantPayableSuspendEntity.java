package com.payment.domain.entity; // 实体包声明

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.FieldStrategy;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.math.BigDecimal; // 高精度金额类型
import java.time.LocalDateTime; // 日期时间类型

/**
 * 商户应付挂账实体
 * 对应数据库表 merchant_payable_suspend
 */
@TableName("merchant_payable_suspend") // 映射到 merchant_payable_suspend 表
public class MerchantPayableSuspendEntity {

    /** 记录主键 ID */
    @TableId(value = "id", type = IdType.AUTO) // 数据库自增主键
    public Long id;

    /** 商户 ID */
    @TableField(value = "merchant_id", updateStrategy = FieldStrategy.NEVER) // 非空，列名 merchant_id
    public Long merchantId;

    /** 账单号（唯一） */
    @TableField("bill_no") // 非空且唯一，最大长度 64
    public String billNo;

    /** 挂账金额 */
    @TableField("suspend_amount") // 非空，18 位精度 2 位小数
    public BigDecimal suspendAmount;

    /** 已结算金额 */
    @TableField("settled_amount") // 非空，18 位精度 2 位小数
    public BigDecimal settledAmount;

    /** 挂账状态 */
    public Integer status; // 非空

    /** 创建时间 */
    @TableField(value = "create_time", fill = FieldFill.INSERT) // 非空，列名 create_time
    public LocalDateTime createTime;
}
