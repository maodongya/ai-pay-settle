package com.payment.domain.entity; // 实体包

import com.baomidou.mybatisplus.annotation.FieldFill; // 字段自动填充
import com.baomidou.mybatisplus.annotation.IdType; // 主键策略
import com.baomidou.mybatisplus.annotation.TableField; // 列映射
import com.baomidou.mybatisplus.annotation.TableId; // 主键
import com.baomidou.mybatisplus.annotation.TableName; // 表名

import java.time.LocalDateTime; // 时间类型

/**
 * 异常工单实体，对应 exception_record 表。
 */
@TableName("exception_record") // 映射表名
public class ExceptionRecordEntity {

    /** 主键 ID */
    @TableId(value = "exception_id", type = IdType.AUTO) // 自增主键
    public Long exceptionId;

    /** 工单号，如 EX202607120001 */
    @TableField("exception_no") // 列 exception_no
    public String exceptionNo;

    /** 异常编码，如 EX-0201 */
    @TableField("exception_code") // 列 exception_code
    public String exceptionCode;

    /** 严重级别：0 P0 … 3 P3 */
    public Integer severity; // 列 severity

    /** 业务域：CLEAR / SETTLE / MQ 等 */
    @TableField("biz_domain") // 列 biz_domain
    public String bizDomain;

    /** 业务键：bill_no / consumerGroup 等 */
    @TableField("biz_key") // 列 biz_key
    public String bizKey;

    /** 标题摘要 */
    public String title; // 列 title

    /** 详情 JSON 或文本 */
    public String detail; // 列 detail

    /** 状态：0 待处理 1 处理中 2 已解决 3 已忽略 */
    public Integer status; // 列 status

    /** 创建时间 */
    @TableField(value = "create_time", fill = FieldFill.INSERT) // 插入时填充
    public LocalDateTime createTime;
}
