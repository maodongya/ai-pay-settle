package com.payment.domain.entity; // 实体包声明

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.LocalDateTime; // 日期时间类型

/**
 * 告警记录实体
 * 对应数据库表 alert_record
 */
@TableName("alert_record") // 映射到 alert_record 表
public class AlertRecordEntity {

    /** 告警主键 ID */
    @TableId(value = "alert_id", type = IdType.AUTO) // 数据库自增主键
    public Long alertId;

    /** 告警类型 */
    @TableField("alert_type") // 非空，最大长度 32
    public String alertType;

    /** 告警级别 */
    public Integer level; // 非空

    /** 告警内容 */
    public String content; // 非空，最大长度 512

    /** 告警状态 */
    public Integer status; // 非空

    /** 创建时间 */
    @TableField(value = "create_time", fill = FieldFill.INSERT) // 非空，列名 create_time
    public LocalDateTime createTime;
}
