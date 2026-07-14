package com.payment.domain.entity; // 实体包声明

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.FieldStrategy;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.LocalDateTime; // 日期时间类型

/**
 * 发件箱消息实体
 * 对应数据库表 outbox_message，用于可靠消息投递
 */
@TableName("outbox_message") // 映射到 outbox_message 表
public class OutboxMessageEntity {

    /** 消息主键 ID */
    @TableId(value = "id", type = IdType.AUTO) // 数据库自增主键
    public Long id;

    /** 业务唯一键 */
    @TableField("biz_key") // 非空，最大长度 64
    public String bizKey;

    @TableField(value = "merchant_id", updateStrategy = FieldStrategy.NEVER)
    public Long merchantId;

    /** 消息主题 */
    public String topic; // 非空，最大长度 64

    /** 消息载荷（JSON 等） */
    public String payload; // 非空，TEXT 类型

    /** 消息状态 */
    public Integer status; // 非空

    /** 创建时间 */
    @TableField(value = "create_time", fill = FieldFill.INSERT) // 非空，列名 create_time
    public LocalDateTime createTime;
}
