package com.payment.domain.entity; // 实体包声明

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.math.BigDecimal; // 高精度金额类型
import java.time.LocalDateTime; // 日期时间类型

/**
 * 会计凭证实体
 * 对应数据库表 account_voucher
 */
@TableName("account_voucher") // 映射到 account_voucher 表
public class AccountVoucherEntity {

    /** 凭证主键 ID */
    @TableId(value = "voucher_id", type = IdType.AUTO) // 数据库自增主键
    public Long voucherId;

    /** 关联账单号 */
    @TableField("bill_no") // 非空，最大长度 64
    public String billNo;

    @TableField("merchant_id")
    public Long merchantId;

    /** 借方科目 */
    @TableField("debit_subject") // 非空，最大长度 32
    public String debitSubject;

    /** 贷方科目 */
    @TableField("credit_subject") // 非空，最大长度 32
    public String creditSubject;

    /** 凭证金额 */
    public BigDecimal amount; // 非空，18 位精度 2 位小数

    /** 同步状态 */
    @TableField("sync_status") // 非空，列名 sync_status
    public Integer syncStatus;

    /** 创建时间 */
    @TableField(value = "create_time", fill = FieldFill.INSERT) // 非空，列名 create_time
    public LocalDateTime createTime;
}
