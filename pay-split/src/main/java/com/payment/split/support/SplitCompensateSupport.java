package com.payment.split.support;

import com.baomidou.dynamic.datasource.annotation.DSTransactional;
import com.payment.api.dto.AgentRelationDTO;
import com.payment.api.dto.FeeCalcResultDTO;
import com.payment.api.service.MerchantValidateService;
import com.payment.api.service.SplitService;
import org.springframework.stereotype.Component;

/**
 * 分账补偿：独立短事务补录。
 */
@Component
public class SplitCompensateSupport {

    private final SplitService splitService;
    private final MerchantValidateService merchantValidateService;

    public SplitCompensateSupport(SplitService splitService, MerchantValidateService merchantValidateService) {
        this.splitService = splitService;
        this.merchantValidateService = merchantValidateService;
    }

    @DSTransactional
    public void compensate(FeeCalcResultDTO result) {
        AgentRelationDTO relation = merchantValidateService.loadRelation(result.merchantId);
        splitService.generateSplitDetail(result, relation);
    }
}
