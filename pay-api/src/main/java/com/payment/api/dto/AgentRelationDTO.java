package com.payment.api.dto; // API 数据传输对象所在包

/**
 * 代理关系 DTO，封装商户与各级代理的关联信息
 */
public class AgentRelationDTO {
    public Long merchantId; // 商户 ID
    public Long agentId; // 一级代理 ID
    public Long secondAgentId; // 二级代理 ID
    public Long splitPartyId; // 分润方 ID
}
