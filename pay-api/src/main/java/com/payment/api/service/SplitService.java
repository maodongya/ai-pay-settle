package com.payment.api.service; // API 服务接口所在包

import com.payment.api.dto.FeeCalcResultDTO; // 费用计算结果 DTO

/**
 * 分润明细服务接口，生成分润拆分明细
 */
public interface SplitService {
    /**
     * 根据费用计算结果和代理关系生成分润明细
     *
     * @param calcResult 费用计算结果
     * @param relation   代理关系信息
     */
    void generateSplitDetail(FeeCalcResultDTO calcResult, com.payment.api.dto.AgentRelationDTO relation);
}
