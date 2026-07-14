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
 * 交易账单实体
 * 对应数据库表 trade_bill
 */
@TableName("trade_bill") // 映射到 trade_bill 表
public class TradeBillEntity {

    /** 记录主键 ID */
    @TableId(value = "id", type = IdType.AUTO) // 数据库自增主键
    public Long id;

    /** 账单号（唯一） */
    @TableField("bill_no") // 非空且唯一，最大长度 64
    public String billNo;

    /** 账单类型 */
    @TableField("bill_type") // 非空，列名 bill_type
    public Integer billType;

    /** 业务线 */
    @TableField("business_line") // 非空，最大长度 32
    public String businessLine;

    /** 品类 */
    public String category; // 非空，最大长度 32

    /** 服务项目 */
    @TableField("service_item") // 非空，最大长度 32
    public String serviceItem;

    /** 商户 ID */
    @TableField(value = "merchant_id", updateStrategy = FieldStrategy.NEVER) // 非空，列名 merchant_id
    public Long merchantId;

    /** 一级代理 ID */
    @TableField("agent_id") // 可选，列名 agent_id
    public Long agentId;

    /** 二级代理 ID */
    @TableField("second_agent_id") // 可选，列名 second_agent_id
    public Long secondAgentId;

    /** 订单号 */
    @TableField("order_no") // 非空，最大长度 64
    public String orderNo;

    /** 原账单号（退款/冲正等场景） */
    @TableField("origin_bill_no") // 可选，最大长度 64
    public String originBillNo;

    /** 交易金额 */
    @TableField("trade_amount") // 非空，18 位精度 2 位小数
    public BigDecimal tradeAmount;

    /** 城市编码 */
    @TableField("city_code") // 非空，最大长度 16
    public String cityCode;

    /** 支付渠道 */
    @TableField("pay_channel") // 可选，最大长度 32
    public String payChannel;

    /** 账单状态 */
    public Integer status; // 非空

    /** 创建时间 */
    @TableField(value = "create_time", fill = FieldFill.INSERT) // 非空，列名 create_time
    public LocalDateTime createTime;

    /** 更新时间 */
    @TableField(value = "update_time", fill = FieldFill.INSERT_UPDATE) // 非空，列名 update_time
    public LocalDateTime updateTime;
}
