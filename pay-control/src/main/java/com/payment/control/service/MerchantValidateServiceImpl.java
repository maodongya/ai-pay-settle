package com.payment.control.service; // 管控服务包

import com.payment.api.dto.AgentRelationDTO; // 代理关系 DTO
import com.payment.api.dto.TradeBillDTO; // 交易账单 DTO
import com.payment.api.dto.ValidateResult; // 校验结果 DTO
import com.payment.api.service.MerchantValidateService; // 商户校验服务接口
import com.payment.common.cache.CacheNames;
import com.payment.common.enums.BillType; // 账单类型枚举
import com.payment.common.exception.ErrorCode; // 错误码
import com.payment.domain.entity.AgentMerchantRelationEntity; // 代理商户关系实体
import com.payment.domain.entity.MerchantProfileEntity; // 商户档案实体
import com.payment.domain.entity.TradeBillEntity; // 交易账单实体
import com.payment.domain.repository.AgentMerchantRelationRepository; // 代理商户关系仓储
import com.payment.domain.repository.MerchantProfileRepository; // 商户档案仓储
import com.payment.domain.repository.TradeBillRepository; // 交易账单仓储
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service; // Spring 服务注解

import java.math.BigDecimal; // 高精度数值
import java.time.LocalDateTime; // 本地日期时间

/**
 * 商户校验服务实现，校验账单合法性和加载代理关系。
 */
@Service // 注册为 Spring 服务
public class MerchantValidateServiceImpl implements MerchantValidateService {

    private final MerchantProfileRepository merchantProfileRepository; // 商户档案仓储
    private final AgentMerchantRelationRepository relationRepository; // 代理关系仓储
    private final TradeBillRepository tradeBillRepository; // 交易账单仓储

    /**
     * 构造注入依赖。
     */
    public MerchantValidateServiceImpl(MerchantProfileRepository merchantProfileRepository,
                                       AgentMerchantRelationRepository relationRepository,
                                       TradeBillRepository tradeBillRepository) {
        this.merchantProfileRepository = merchantProfileRepository; // 赋值档案仓储
        this.relationRepository = relationRepository; // 赋值关系仓储
        this.tradeBillRepository = tradeBillRepository; // 赋值账单仓储
    }

    /**
     * 校验交易账单的合法性。
     */
    @Override // 实现接口方法
    public ValidateResult validateBill(TradeBillDTO bill) {
        if (bill.tradeAmount == null || bill.tradeAmount.compareTo(BigDecimal.ZERO) <= 0) { // 金额无效
            return ValidateResult.fail(ErrorCode.INVALID_PARAM.getCode(), "amount invalid");
        }
        MerchantProfileEntity profile = merchantProfileRepository.findById(bill.merchantId).orElse(null); // 查询商户档案（带缓存）
        if (profile == null || profile.status != 1) { // 商户不存在或未启用
            return ValidateResult.fail(ErrorCode.MERCHANT_INVALID.getCode(), ErrorCode.MERCHANT_INVALID.getMessage());
        }
        if (bill.billType == BillType.REFUND.getCode()) { // 退款单
            if (bill.originBillNo == null) { // 缺少原单号
                return ValidateResult.fail(ErrorCode.INVALID_PARAM.getCode(), "originBillNo required");
            }
            TradeBillEntity origin = tradeBillRepository.findByBillNo(bill.originBillNo).orElse(null); // 查询原单
            if (origin == null) { // 原单不存在
                return ValidateResult.fail(ErrorCode.ORIGIN_BILL_NOT_FOUND.getCode(), ErrorCode.ORIGIN_BILL_NOT_FOUND.getMessage());
            }
        }
        return ValidateResult.ok(); // 校验通过
    }

    /**
     * 加载商户的代理关系，无有效关系时返回空 DTO。结果缓存到 Redis。
     */
    @Override // 实现接口方法
    @Cacheable(cacheNames = CacheNames.AGENT_RELATION, key = "#merchantId + ':dto'")
    public AgentRelationDTO loadRelation(Long merchantId) {
        return relationRepository.findFirstByMerchantIdOrderByRelIdDesc(merchantId) // 查询最新关系（仓储层亦有缓存）
                .filter(this::isActive) // 过滤有效关系
                .map(this::toDto) // 转为 DTO
                .orElseGet(() -> { // 无有效关系
                    AgentRelationDTO dto = new AgentRelationDTO(); // 创建空 DTO
                    dto.merchantId = merchantId; // 设置商户 ID
                    return dto; // 返回
                });
    }

    /**
     * 判断代理关系是否在有效期内。
     */
    private boolean isActive(AgentMerchantRelationEntity rel) {
        LocalDateTime now = LocalDateTime.now(); // 当前时间
        return !rel.validStart.isAfter(now) // 已生效
                && (rel.validEnd == null || rel.validEnd.isAfter(now)); // 未过期
    }

    /**
     * 将代理关系实体转换为 DTO。
     */
    private AgentRelationDTO toDto(AgentMerchantRelationEntity rel) {
        AgentRelationDTO dto = new AgentRelationDTO(); // 创建 DTO
        dto.merchantId = rel.merchantId; // 商户 ID
        dto.agentId = rel.agentId; // 一级代理 ID
        dto.secondAgentId = rel.secondAgentId; // 二级代理 ID
        dto.splitPartyId = rel.splitPartyId; // 合作方 ID
        return dto; // 返回 DTO
    }
}
