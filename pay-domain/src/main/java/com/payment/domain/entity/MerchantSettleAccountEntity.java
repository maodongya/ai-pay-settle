package com.payment.domain.entity; // 实体包声明

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import com.baomidou.mybatisplus.annotation.Version;

import java.math.BigDecimal; // 高精度金额类型
import java.time.LocalDateTime; // 日期时间类型

/**
 * 商户结算账户实体
 * 对应数据库表 merchant_settle_account
 */
@TableName("merchant_settle_account") // 映射到 merchant_settle_account 表
public class MerchantSettleAccountEntity {

    /** 账户主键 ID */
    @TableId(value = "account_id", type = IdType.AUTO) // 数据库自增主键
    public Long accountId;

    /** 商户 ID（唯一） */
    @TableField("merchant_id") // 非空且唯一，列名 merchant_id
    public Long merchantId;

    /** 待结算余额 */
    @TableField("wait_balance") // 非空，18 位精度 2 位小数
    public BigDecimal waitBalance;

    /** 冻结余额 */
    @TableField("frozen_balance") // 非空，18 位精度 2 位小数
    public BigDecimal frozenBalance;

    /** 结算银行卡号 */
    @TableField("settle_card_no") // 可选，最大长度 128
    public String settleCardNo;

    /** 结算方式 */
    @TableField("settle_mode") // 非空，列名 settle_mode
    public Integer settleMode;

    /** 乐观锁版本号 */
    @Version // 乐观锁版本字段
    public Integer version;

    /** 创建时间 */
    @TableField(value = "create_time", fill = FieldFill.INSERT) // 非空，列名 create_time
    public LocalDateTime createTime;

    /** 更新时间 */
    @TableField(value = "update_time", fill = FieldFill.INSERT_UPDATE) // 非空，列名 update_time
    public LocalDateTime updateTime;
}
