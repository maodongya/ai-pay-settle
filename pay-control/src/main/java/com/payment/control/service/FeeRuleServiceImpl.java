package com.payment.control.service; // 管控服务包

import com.payment.api.dto.FeeRuleSubmitDTO; // 费率规则提交 DTO
import com.payment.api.dto.FeeRuleSubmitResultDTO; // 费率规则提交结果 DTO
import com.payment.api.service.FeeRuleService; // 费率规则服务接口
import com.payment.common.exception.BizException; // 业务异常
import com.payment.common.exception.ErrorCode; // 错误码
import com.payment.domain.entity.FeeShareRuleEntity; // 分润规则实体
import com.payment.domain.repository.FeeShareRuleRepository; // 分润规则仓储
import org.springframework.stereotype.Service; // Spring 服务注解
import org.springframework.transaction.annotation.Transactional; // 事务注解

import java.time.LocalDateTime; // 本地日期时间

/**
 * 费率规则服务实现，负责分润规则的提交与保存。
 */
@Service // 注册为 Spring 服务
public class FeeRuleServiceImpl implements FeeRuleService {

    private final FeeShareRuleRepository feeShareRuleRepository; // 分润规则仓储

    /**
     * 构造注入依赖。
     */
    public FeeRuleServiceImpl(FeeShareRuleRepository feeShareRuleRepository) {
        this.feeShareRuleRepository = feeShareRuleRepository; // 赋值规则仓储
    }

    /**
     * 提交新的分润规则。
     */
    @Override // 实现接口方法
    @Transactional // 开启事务
    public FeeRuleSubmitResultDTO submitRule(FeeRuleSubmitDTO request) {
        if (request.ruleName == null || request.targetType == null || request.shareMode == null // 校验必填字段
                || request.firstMonthValue == null || request.minShare == null || request.validStart == null) {
            throw BizException.of(ErrorCode.INVALID_PARAM, "missing required fields");
        }

        FeeShareRuleEntity rule = new FeeShareRuleEntity(); // 创建规则实体
        rule.ruleName = request.ruleName; // 规则名称
        rule.targetType = request.targetType; // 目标类型
        rule.businessLine = defaultDim(request.businessLine); // 业务线维度
        rule.category = defaultDim(request.category); // 品类维度
        rule.serviceItem = defaultDim(request.serviceItem); // 服务项目维度
        rule.cityCode = defaultDim(request.cityCode); // 城市维度
        rule.shareMode = request.shareMode; // 分润模式
        rule.firstMonthValue = request.firstMonthValue; // 首月值
        rule.stepDownVal = request.stepDownVal != null ? request.stepDownVal : java.math.BigDecimal.ZERO; // 递减步长
        rule.minShare = request.minShare; // 最低分润
        rule.validStart = request.validStart; // 生效时间
        rule.status = 0; // 待审核状态
        rule.createTime = LocalDateTime.now(); // 创建时间
        feeShareRuleRepository.save(rule); // 保存规则

        FeeRuleSubmitResultDTO result = new FeeRuleSubmitResultDTO(); // 创建结果 DTO
        result.ruleId = rule.ruleId; // 规则 ID
        result.status = rule.status; // 规则状态
        return result; // 返回结果
    }

    /**
     * 维度值默认值处理，空值转为通配符 *。
     */
    private String defaultDim(String value) {
        return value == null || value.isBlank() ? "*" : value; // 空或空白则通配
    }
}
