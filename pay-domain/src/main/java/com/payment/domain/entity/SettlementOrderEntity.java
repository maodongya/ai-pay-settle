package com.payment.domain.entity; // 实体包声明

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.math.BigDecimal; // 高精度金额类型
import java.time.LocalDateTime; // 日期时间类型

/**
 * 结算订单实体
 * 对应数据库表 settlement_order
 */
@TableName("settlement_order") // 映射到 settlement_order 表
public class SettlementOrderEntity {

    /** 记录主键 ID */
    @TableId(value = "id", type = IdType.AUTO) // 数据库自增主键
    public Long id;

    /** 结算单号（唯一） */
    @TableField("settle_no") // 非空且唯一，最大长度 64
    public String settleNo;

    /** 商户 ID */
    @TableField("merchant_id") // 非空，列名 merchant_id
    public Long merchantId;

    /** 结算金额 */
    @TableField("settle_amount") // 非空，18 位精度 2 位小数
    public BigDecimal settleAmount;

    /** 结算方式 */
    @TableField("settle_mode") // 非空，列名 settle_mode
    public Integer settleMode;

    /** 结算银行卡号 */
    @TableField("settle_card_no") // 非空，最大长度 128
    public String settleCardNo;

    /** 结算状态 */
    public Integer status; // 非空

    /** 渠道交易号 */
    @TableField("channel_trade_no") // 可选，最大长度 64
    public String channelTradeNo;

    /** 失败原因 */
    @TableField("fail_reason") // 可选，最大长度 256
    public String failReason;

    /** 原结算单号（冲正等场景） */
    @TableField("origin_settle_no") // 可选，最大长度 64
    public String originSettleNo;

    /** 创建时间 */
    @TableField(value = "create_time", fill = FieldFill.INSERT) // 非空，列名 create_time
    public LocalDateTime createTime;

    /** 更新时间 */
    @TableField(value = "update_time", fill = FieldFill.INSERT_UPDATE) // 非空，列名 update_time
    public LocalDateTime updateTime;
}
