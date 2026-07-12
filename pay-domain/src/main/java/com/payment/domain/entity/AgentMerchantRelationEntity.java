package com.payment.domain.entity; // 实体包声明

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.time.LocalDateTime; // 日期时间类型

/**
 * 代理商户关系实体
 * 对应数据库表 agent_merchant_relation
 */
@TableName("agent_merchant_relation") // 映射到 agent_merchant_relation 表
public class AgentMerchantRelationEntity {

    /** 关系主键 ID */
    @TableId(value = "rel_id", type = IdType.AUTO) // 数据库自增主键
    public Long relId;

    /** 一级代理 ID */
    @TableField("agent_id") // 非空，列名 agent_id
    public Long agentId;

    /** 二级代理 ID */
    @TableField("second_agent_id") // 可选，列名 second_agent_id
    public Long secondAgentId;

    /** 商户 ID */
    @TableField("merchant_id") // 非空，列名 merchant_id
    public Long merchantId;

    /** 分账参与方 ID */
    @TableField("split_party_id") // 可选，列名 split_party_id
    public Long splitPartyId;

    /** 关系生效开始时间 */
    @TableField("valid_start") // 非空，列名 valid_start
    public LocalDateTime validStart;

    /** 关系生效结束时间 */
    @TableField("valid_end") // 可选，列名 valid_end
    public LocalDateTime validEnd;
}
