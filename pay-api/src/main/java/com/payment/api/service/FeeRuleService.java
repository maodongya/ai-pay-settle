package com.payment.api.service; // API 服务接口所在包

import com.payment.api.dto.FeeRuleSubmitDTO; // 费率规则提交 DTO
import com.payment.api.dto.FeeRuleSubmitResultDTO; // 费率规则提交结果 DTO

/**
 * 费率规则服务接口，提供规则提交能力
 */
public interface FeeRuleService {
    /**
     * 提交费率规则
     *
     * @param request 规则提交参数
     * @return 提交结果
     */
    FeeRuleSubmitResultDTO submitRule(FeeRuleSubmitDTO request);
}
