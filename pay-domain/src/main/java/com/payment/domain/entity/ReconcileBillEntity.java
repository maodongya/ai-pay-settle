package com.payment.domain.entity; // 实体包声明

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.math.BigDecimal; // 高精度金额类型
import java.time.LocalDate; // 日期类型
import java.time.LocalDateTime; // 日期时间类型

/**
 * 对账账单实体
 * 对应数据库表 reconcile_bill
 */
@TableName("reconcile_bill") // 映射到 reconcile_bill 表
public class ReconcileBillEntity {

    /** 账单主键 ID */
    @TableId(value = "bill_id", type = IdType.AUTO) // 数据库自增主键
    public Long billId;

    /** 商户 ID */
    @TableField("merchant_id") // 非空，列名 merchant_id
    public Long merchantId;

    /** 账单日期 */
    @TableField("bill_date") // 非空，列名 bill_date
    public LocalDate billDate;

    /** 总收入金额 */
    @TableField("total_income") // 非空，18 位精度 2 位小数
    public BigDecimal totalIncome;

    /** 总结算金额 */
    @TableField("total_settle") // 非空，18 位精度 2 位小数
    public BigDecimal totalSettle;

    /** 对账文件 URL */
    @TableField("file_url") // 可选，最大长度 256
    public String fileUrl;

    /** 创建时间 */
    @TableField(value = "create_time", fill = FieldFill.INSERT) // 非空，列名 create_time
    public LocalDateTime createTime;
}
