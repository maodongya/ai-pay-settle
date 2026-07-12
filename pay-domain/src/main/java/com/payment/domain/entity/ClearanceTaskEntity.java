package com.payment.domain.entity; // 实体包声明

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.LocalDateTime; // 日期时间类型

/**
 * 清算任务实体
 * 对应数据库表 clearance_task
 */
@TableName("clearance_task") // 映射到 clearance_task 表
public class ClearanceTaskEntity {

    /** 任务主键 ID */
    @TableId(value = "task_id", type = IdType.AUTO) // 数据库自增主键
    public Long taskId;

    /** 账单号（唯一） */
    @TableField("bill_no") // 非空且唯一，最大长度 64
    public String billNo;

    /** 商户 ID */
    @TableField("merchant_id") // 非空，列名 merchant_id
    public Long merchantId;

    /** 分片 ID */
    @TableField("shard_id") // 非空，列名 shard_id
    public Integer shardId;

    /** 任务状态 */
    public Integer status; // 非空

    /** 重试次数 */
    @TableField("retry_count") // 非空，列名 retry_count
    public Integer retryCount;

    /** 错误信息 */
    @TableField("error_msg") // 可选，最大长度 512
    public String errorMsg;

    /** 创建时间 */
    @TableField(value = "create_time", fill = FieldFill.INSERT) // 非空，列名 create_time
    public LocalDateTime createTime;

    /** 更新时间 */
    @TableField(value = "update_time", fill = FieldFill.INSERT_UPDATE) // 非空，列名 update_time
    public LocalDateTime updateTime;
}
