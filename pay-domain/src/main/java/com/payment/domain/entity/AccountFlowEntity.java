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
 * 账户流水实体
 * 对应数据库表 account_flow
 */
@TableName("account_flow") // 映射到 account_flow 表
public class AccountFlowEntity {

    /** 流水主键 ID */
    @TableId(value = "flow_id", type = IdType.AUTO) // 数据库自增主键
    public Long flowId;

    /** 商户 ID */
    @TableField(value = "merchant_id", updateStrategy = FieldStrategy.NEVER) // 非空，列名 merchant_id
    public Long merchantId;

    /** 关联账单号 */
    @TableField("bill_no") // 可选，最大长度 64
    public String billNo;

    /** 关联结算单号 */
    @TableField("settle_no") // 可选，最大长度 64
    public String settleNo;

    /** 操作类型 */
    @TableField("op_type") // 非空，列名 op_type
    public Integer opType;

    /** 变动金额 */
    public BigDecimal amount; // 非空，18 位精度 2 位小数

    /** 变动前余额 */
    @TableField("before_balance") // 非空，18 位精度 2 位小数
    public BigDecimal beforeBalance;

    /** 变动后余额 */
    @TableField("after_balance") // 非空，18 位精度 2 位小数
    public BigDecimal afterBalance;

    /** 创建时间 */
    @TableField(value = "create_time", fill = FieldFill.INSERT) // 非空，列名 create_time
    public LocalDateTime createTime;
}
