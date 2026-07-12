package com.payment.domain.entity; // 实体包声明

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.math.BigDecimal; // 高精度金额类型
import java.time.LocalDateTime; // 日期时间类型

/**
 * 费用分润规则实体
 * 对应数据库表 fee_share_rule
 */
@TableName("fee_share_rule") // 映射到 fee_share_rule 表
public class FeeShareRuleEntity {

    /** 规则主键 ID */
    @TableId(value = "rule_id", type = IdType.AUTO) // 数据库自增主键
    public Long ruleId;

    /** 规则名称 */
    @TableField("rule_name") // 非空，最大长度 128
    public String ruleName;

    /** 适用目标类型 */
    @TableField("target_type") // 非空，列名 target_type
    public Integer targetType;

    /** 业务线 */
    @TableField("business_line") // 非空，最大长度 32
    public String businessLine;

    /** 品类 */
    public String category; // 非空，最大长度 32

    /** 服务项目 */
    @TableField("service_item") // 非空，最大长度 32
    public String serviceItem;

    /** 城市编码 */
    @TableField("city_code") // 非空，最大长度 16
    public String cityCode;

    /** 分润模式 */
    @TableField("share_mode") // 非空，列名 share_mode
    public Integer shareMode;

    /** 首月分润值 */
    @TableField("first_month_value") // 非空，18 位精度 4 位小数
    public BigDecimal firstMonthValue;

    /** 阶梯递减值 */
    @TableField("step_down_val") // 可选，18 位精度 4 位小数
    public BigDecimal stepDownVal;

    /** 最低分润值 */
    @TableField("min_share") // 非空，18 位精度 4 位小数
    public BigDecimal minShare;

    /** 生效开始时间 */
    @TableField("valid_start") // 非空，列名 valid_start
    public LocalDateTime validStart;

    /** 生效结束时间 */
    @TableField("valid_end") // 可选，列名 valid_end
    public LocalDateTime validEnd;

    /** 规则状态 */
    public Integer status; // 非空

    /** 创建时间 */
    @TableField(value = "create_time", fill = FieldFill.INSERT) // 非空，列名 create_time
    public LocalDateTime createTime;
}
