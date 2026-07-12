package com.payment.domain.entity; // 实体包声明

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.math.BigDecimal; // 高精度金额类型
import java.time.LocalDateTime; // 日期时间类型

/**
 * 费用计算结果实体
 * 对应数据库表 fee_calc_result
 */
@TableName("fee_calc_result") // 映射到 fee_calc_result 表
public class FeeCalcResultEntity {

    /** 记录主键 ID */
    @TableId(value = "id", type = IdType.AUTO) // 数据库自增主键
    public Long id;

    /** 账单号（唯一） */
    @TableField("bill_no") // 非空且唯一，最大长度 64
    public String billNo;

    /** 商户 ID */
    @TableField("merchant_id") // 非空，列名 merchant_id
    public Long merchantId;

    /** 交易金额 */
    @TableField("trade_amount") // 非空，18 位精度 2 位小数
    public BigDecimal tradeAmount;

    /** 平台手续费 */
    @TableField("platform_fee") // 非空，18 位精度 2 位小数
    public BigDecimal platformFee;

    /** 一级代理分润 */
    @TableField("agent_l1_share") // 非空，18 位精度 2 位小数
    public BigDecimal agentL1Share;

    /** 二级代理分润 */
    @TableField("agent_l2_share") // 非空，18 位精度 2 位小数
    public BigDecimal agentL2Share;

    /** 合作方分润 */
    @TableField("partner_share") // 非空，18 位精度 2 位小数
    public BigDecimal partnerShare;

    /** 商户实收金额 */
    @TableField("merchant_income") // 非空，18 位精度 2 位小数
    public BigDecimal merchantIncome;

    /** 规则快照（JSON 等） */
    @TableField("rule_snapshot") // TEXT 类型，可选
    public String ruleSnapshot;

    /** 计算时间 */
    @TableField("calc_time") // 非空，列名 calc_time
    public LocalDateTime calcTime;
}
