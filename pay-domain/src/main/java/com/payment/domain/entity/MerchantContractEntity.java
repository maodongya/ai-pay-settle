package com.payment.domain.entity; // 实体包声明

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.math.BigDecimal; // 高精度金额类型
import java.time.LocalDate; // 日期类型

/**
 * 商户合同实体
 * 对应数据库表 merchant_contract
 */
@TableName("merchant_contract") // 映射到 merchant_contract 表
public class MerchantContractEntity {

    /** 合同主键 ID */
    @TableId(value = "contract_id", type = IdType.AUTO) // 数据库自增主键
    public Long contractId;

    /** 商户 ID（唯一） */
    @TableField("merchant_id") // 非空且唯一，列名 merchant_id
    public Long merchantId;

    /** 签约日期 */
    @TableField("sign_date") // 非空，列名 sign_date
    public LocalDate signDate;

    /** 结算方式 */
    @TableField("settle_mode") // 非空，列名 settle_mode
    public Integer settleMode;

    /** 最低提现金额 */
    @TableField("min_withdraw") // 非空，18 位精度 2 位小数
    public BigDecimal minWithdraw;

    /** 合同状态 */
    public Integer status; // 非空
}
