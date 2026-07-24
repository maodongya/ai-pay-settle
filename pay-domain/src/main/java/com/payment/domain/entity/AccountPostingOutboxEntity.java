package com.payment.domain.entity;

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.FieldStrategy;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.LocalDateTime;

/**
 * 账务过账发件箱实体。
 */
@TableName("account_posting_outbox")
public class AccountPostingOutboxEntity {

    @TableId(value = "id", type = IdType.AUTO)
    public Long id;

    @TableField("tenant_id")
    public String tenantId;

    @TableField(value = "merchant_id", updateStrategy = FieldStrategy.NEVER)
    public Long merchantId;

    @TableField("biz_no")
    public String bizNo;

    @TableField("biz_type")
    public String bizType;

    @TableField("payload_json")
    public String payloadJson;

    public Integer status;

    @TableField("retry_count")
    public Integer retryCount;

    @TableField("last_error")
    public String lastError;

    @TableField("transaction_no")
    public String transactionNo;

    @TableField(value = "create_time", fill = FieldFill.INSERT)
    public LocalDateTime createTime;

    @TableField(value = "update_time", fill = FieldFill.INSERT_UPDATE)
    public LocalDateTime updateTime;
}
