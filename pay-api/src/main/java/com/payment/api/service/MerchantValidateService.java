package com.payment.api.service; // API 服务接口所在包

import com.payment.api.dto.AgentRelationDTO; // 代理关系 DTO
import com.payment.api.dto.TradeBillDTO; // 交易账单 DTO
import com.payment.api.dto.ValidateResult; // 校验结果 DTO

/**
 * 商户校验服务接口，提供账单校验与代理关系查询
 */
public interface MerchantValidateService {
    /**
     * 校验交易账单合法性
     *
     * @param bill 待校验账单
     * @return 校验结果
     */
    ValidateResult validateBill(TradeBillDTO bill);

    /**
     * 加载商户代理关系
     *
     * @param merchantId 商户 ID
     * @return 代理关系信息
     */
    AgentRelationDTO loadRelation(Long merchantId);
}
