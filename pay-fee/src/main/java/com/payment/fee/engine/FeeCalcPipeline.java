package com.payment.fee.engine; // 费用引擎包

import com.payment.api.dto.FeeCalcDTO; // 费用计算请求 DTO
import com.payment.api.dto.FeeCalcResultDTO; // 费用计算结果 DTO
import com.payment.common.enums.ShareMode; // 分润模式枚举
import com.payment.common.enums.TargetType; // 分润目标类型枚举
import com.payment.common.exception.BizException; // 业务异常
import com.payment.common.exception.ErrorCode; // 错误码
import com.payment.common.util.MoneyUtils; // 金额工具类
import com.payment.domain.entity.FeeShareRuleEntity; // 分润规则实体
import com.payment.domain.repository.MerchantContractRepository; // 商户合约仓储
import org.springframework.stereotype.Component; // Spring 组件注解

import java.math.BigDecimal; // 高精度数值
import java.time.LocalDate; // 本地日期
import java.time.temporal.ChronoUnit; // 时间单位
import java.util.ArrayList; // 动态数组列表
import java.util.HashMap; // 哈希映射
import java.util.List; // 列表
import java.util.Map; // 映射

/**
 * 费用计算流水线，按平台→代理→合作方→商户顺序逐级分润。
 */
@Component // 注册为 Spring 组件
public class FeeCalcPipeline {

    private final RuleMatcher ruleMatcher; // 规则匹配器
    private final MerchantContractRepository merchantContractRepository; // 商户合约仓储

    /**
     * 构造注入依赖。
     */
    public FeeCalcPipeline(RuleMatcher ruleMatcher, MerchantContractRepository merchantContractRepository) {
        this.ruleMatcher = ruleMatcher; // 赋值规则匹配器
        this.merchantContractRepository = merchantContractRepository; // 赋值合约仓储
    }

    /**
     * 执行完整分润流水线计算。
     *
     * @param req   费用计算请求
     * @param rules 候选规则列表
     * @return 分润计算结果
     */
    public FeeCalcResultDTO execute(FeeCalcDTO req, List<FeeShareRuleEntity> rules) {
        RuleMatcher.FeeMatchContext ctx = new RuleMatcher.FeeMatchContext( // 构建匹配上下文
                req.businessLine, req.category, req.serviceItem, req.cityCode);

        BigDecimal net = req.tradeAmount; // 当前可分润净额，初始为交易金额
        List<Map<String, Object>> pipeline = new ArrayList<>(); // 流水线步骤记录
        Map<String, Object> ruleSnapshot = new HashMap<>(); // 规则快照

        FeeShareRuleEntity platformRule = ruleMatcher.match(rules, ctx, TargetType.PLATFORM); // 匹配平台规则
        BigDecimal platformFee = calcAgentShare(net, platformRule, 1); // 计算平台费
        assertStep("PLATFORM", net, platformFee); // 校验分润步骤
        pipeline.add(step("PLATFORM", net, platformFee, net.subtract(platformFee))); // 记录平台步骤
        net = net.subtract(platformFee); // 扣减平台费后的净额
        ruleSnapshot.put("PLATFORM", ruleMeta(platformRule, null)); // 记录平台规则快照

        BigDecimal l1 = BigDecimal.ZERO; // 一级代理分润，默认零
        if (req.agentId != null) { // 有一级代理时才计算
            FeeShareRuleEntity l1Rule = ruleMatcher.match(rules, ctx, TargetType.AGENT_L1); // 匹配一级代理规则
            l1 = calcAgentShare(net, l1Rule, joinMonths(req.merchantId)); // 计算一级代理分润
            assertStep("AGENT_L1", net, l1); // 校验分润步骤
            pipeline.add(step("AGENT_L1", net, l1, net.subtract(l1))); // 记录一级代理步骤
            net = net.subtract(l1); // 扣减一级代理分润
            ruleSnapshot.put("AGENT_L1", ruleMeta(l1Rule, null)); // 记录一级代理规则快照
        }

        BigDecimal l2 = BigDecimal.ZERO; // 二级代理分润，默认零
        if (req.secondAgentId != null) { // 有二级代理时才计算
            FeeShareRuleEntity l2Rule = ruleMatcher.match(rules, ctx, TargetType.AGENT_L2); // 匹配二级代理规则
            l2 = calcAgentShare(net, l2Rule, joinMonths(req.merchantId)); // 计算二级代理分润
            assertStep("AGENT_L2", net, l2); // 校验分润步骤
            pipeline.add(step("AGENT_L2", net, l2, net.subtract(l2))); // 记录二级代理步骤
            net = net.subtract(l2); // 扣减二级代理分润
            ruleSnapshot.put("AGENT_L2", ruleMeta(l2Rule, null)); // 记录二级代理规则快照
        }

        BigDecimal partner = BigDecimal.ZERO; // 合作方分润，默认零
        if (req.splitPartyId != null) { // 有合作方时才计算
            int months = joinMonths(req.merchantId); // 获取入驻月数
            FeeShareRuleEntity pRule = ruleMatcher.match(rules, ctx, TargetType.PARTNER); // 匹配合作方规则
            ShareMode mode = ShareMode.of(pRule.shareMode); // 解析分润模式
            if (mode == ShareMode.FIXED_AMOUNT) { // 合作方不支持固定金额
                throw BizException.of(ErrorCode.INVALID_PARAM, "partner does not support fixed amount");
            }
            partner = ruleMatcher.calcShare(net, pRule, months); // 计算合作方分润
            assertStep("PARTNER", net, partner); // 校验分润步骤
            pipeline.add(step("PARTNER", net, partner, net.subtract(partner))); // 记录合作方步骤
            net = net.subtract(partner); // 扣减合作方分润
            ruleSnapshot.put("PARTNER", ruleMeta(pRule, ruleMatcher.calcStepDown(pRule, months))); // 记录合作方规则快照
        }

        BigDecimal merchantIncome = MoneyUtils.money(net); // 商户收入为剩余净额
        BigDecimal total = platformFee.add(l1).add(l2).add(partner).add(merchantIncome); // 汇总各方金额
        BigDecimal diff = req.tradeAmount.subtract(total); // 计算舍入差额
        if (diff.compareTo(BigDecimal.ZERO) != 0) { // 存在差额时
            merchantIncome = merchantIncome.add(diff); // 差额归入商户收入
        }

        FeeCalcResultDTO result = new FeeCalcResultDTO(); // 创建结果 DTO
        result.billNo = req.billNo; // 账单号
        result.merchantId = req.merchantId; // 商户 ID
        result.tradeAmount = req.tradeAmount; // 交易金额
        result.platformFee = platformFee; // 平台费
        result.agentL1Share = l1; // 一级代理分润
        result.agentL2Share = l2; // 二级代理分润
        result.partnerShare = partner; // 合作方分润
        result.merchantIncome = merchantIncome; // 商户收入
        result.ruleSnapshotJson = Map.of("rules", ruleSnapshot, "pipeline", pipeline).toString(); // 规则与流水线快照
        return result; // 返回计算结果
    }

    /**
     * 计算代理分润，代理不支持阶梯递减模式。
     */
    private BigDecimal calcAgentShare(BigDecimal net, FeeShareRuleEntity rule, int joinMonths) {
        ShareMode mode = ShareMode.of(rule.shareMode); // 解析分润模式
        if (mode == ShareMode.STEP_DOWN) { // 代理不支持阶梯递减
            throw BizException.of(ErrorCode.INVALID_PARAM, "agent does not support step down");
        }
        return ruleMatcher.calcShare(net, rule, joinMonths); // 调用规则匹配器计算
    }

    /**
     * 校验分润步骤，确保分润非负且剩余净额非负。
     */
    private void assertStep(String step, BigDecimal net, BigDecimal share) {
        if (share.compareTo(BigDecimal.ZERO) < 0) { // 分润为负
            throw new BizException(ErrorCode.NEGATIVE_SHARE.getCode(), step + " negative share");
        }
        if (net.subtract(share).compareTo(BigDecimal.ZERO) < 0) { // 剩余净额为负
            throw new BizException(ErrorCode.NEGATIVE_NET.getCode(), step + " negative net");
        }
    }

    /**
     * 根据商户签约日期计算入驻月数。
     */
    private int joinMonths(Long merchantId) {
        return merchantContractRepository.findByMerchantId(merchantId) // 查询商户合约
                .map(c -> c.signDate) // 取签约日期
                .map(sign -> { // 计算月数
                    LocalDate today = LocalDate.now(); // 今天
                    long months = ChronoUnit.MONTHS.between(sign.withDayOfMonth(1), today.withDayOfMonth(1)); // 月差
                    if (today.getDayOfMonth() < sign.getDayOfMonth()) { // 日未到达签约日
                        months--; // 减一个月
                    }
                    return (int) Math.max(1, months + 1); // 至少 1 个月
                })
                .orElse(1); // 无合约默认 1 个月
    }

    /**
     * 构建流水线单步记录。
     */
    private Map<String, Object> step(String name, BigDecimal base, BigDecimal share, BigDecimal remain) {
        Map<String, Object> m = new HashMap<>(); // 创建步骤映射
        m.put("step", name); // 步骤名称
        m.put("base", base); // 分润基数
        m.put("share", share); // 分润金额
        m.put("remain", remain); // 剩余净额
        return m; // 返回步骤记录
    }

    /**
     * 构建规则元数据快照。
     */
    private Map<String, Object> ruleMeta(FeeShareRuleEntity rule, BigDecimal effectiveRate) {
        Map<String, Object> m = new HashMap<>(); // 创建元数据映射
        m.put("ruleId", rule.ruleId); // 规则 ID
        m.put("shareMode", rule.shareMode); // 分润模式
        m.put("value", rule.firstMonthValue); // 首月值
        if (effectiveRate != null) { // 有有效比例时
            m.put("effectiveRate", effectiveRate); // 记录有效比例
        }
        return m; // 返回元数据
    }
}
