package com.payment.split.support; // 分账支持工具包

import com.payment.api.dto.AgentRelationDTO; // 代理关系 DTO
import com.payment.api.dto.FeeCalcResultDTO; // 费用计算结果 DTO
import com.payment.api.service.MerchantValidateService; // 商户校验服务接口
import com.payment.api.service.SplitService; // 分账服务接口
import org.springframework.stereotype.Component; // Spring 组件注解

/**
 * 分账补偿支持类，用于失败任务的分账补录。
 */
@Component // 注册为 Spring 组件
public class SplitCompensateSupport {

    private final SplitService splitService; // 分账服务
    private final MerchantValidateService merchantValidateService; // 商户校验服务

    /**
     * 构造注入依赖。
     */
    public SplitCompensateSupport(SplitService splitService, MerchantValidateService merchantValidateService) {
        this.splitService = splitService; // 赋值分账服务
        this.merchantValidateService = merchantValidateService; // 赋值校验服务
    }

    /**
     * 对已有费用计算结果执行分账补偿。
     */
    public void compensate(FeeCalcResultDTO result) {
        AgentRelationDTO relation = merchantValidateService.loadRelation(result.merchantId); // 加载代理关系
        splitService.generateSplitDetail(result, relation); // 生成分账明细
    }
}
