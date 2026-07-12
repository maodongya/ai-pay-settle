package com.payment.fee.engine; // 费用引擎包

import com.payment.common.enums.ShareMode; // 分润模式枚举
import com.payment.common.enums.TargetType; // 分润目标类型枚举
import com.payment.common.exception.BizException; // 业务异常
import com.payment.common.exception.ErrorCode; // 错误码
import com.payment.common.util.MoneyUtils; // 金额工具类
import com.payment.domain.entity.FeeShareRuleEntity; // 分润规则实体
import org.springframework.stereotype.Component; // Spring 组件注解

import java.math.BigDecimal; // 高精度数值
import java.time.LocalDateTime; // 本地日期时间
import java.util.Comparator; // 比较器
import java.util.List; // 列表
import java.util.Objects; // 对象工具

/**
 * 分润规则匹配器，负责按维度匹配规则并计算分润金额。
 */
@Component // 注册为 Spring 组件
public class RuleMatcher {

    /**
     * 从规则列表中匹配指定目标类型的最优规则。
     *
     * @param rules      候选规则列表
     * @param ctx        匹配上下文
     * @param targetType 目标类型
     * @return 匹配到的规则实体
     */
    public FeeShareRuleEntity match(List<FeeShareRuleEntity> rules, FeeMatchContext ctx, TargetType targetType) {
        LocalDateTime now = LocalDateTime.now(); // 当前时间，用于有效期校验
        return rules.stream() // 将规则列表转为流
                .filter(r -> Objects.equals(r.targetType, targetType.getCode())) // 过滤目标类型一致
                .filter(r -> r.status == 1) // 过滤启用状态
                .filter(r -> !r.validStart.isAfter(now)) // 过滤已生效
                .filter(r -> r.validEnd == null || r.validEnd.isAfter(now)) // 过滤未过期
                .filter(r -> dimensionMatch(r.businessLine, ctx.businessLine())) // 匹配业务线维度
                .filter(r -> dimensionMatch(r.category, ctx.category())) // 匹配品类维度
                .filter(r -> dimensionMatch(r.serviceItem, ctx.serviceItem())) // 匹配服务项目维度
                .filter(r -> dimensionMatch(r.cityCode, ctx.cityCode())) // 匹配城市维度
                .max(Comparator.<FeeShareRuleEntity>comparingInt(r -> specificity(r, ctx)) // 按匹配精确度取最大
                        .thenComparing(r -> r.validStart)) // 精确度相同时取生效时间较晚的
                .orElseThrow(() -> BizException.of(ErrorCode.RULE_NOT_MATCHED, targetType.name())); // 无匹配则抛异常
    }

    /**
     * 判断规则维度值是否与请求值匹配（通配符 * 表示任意）。
     */
    private boolean dimensionMatch(String ruleVal, String reqVal) {
        return "*".equals(ruleVal) || ruleVal.equals(reqVal); // 通配符或精确相等
    }

    /**
     * 计算规则与请求的匹配精确度得分，得分越高越精确。
     */
    int specificity(FeeShareRuleEntity rule, FeeMatchContext ctx) {
        int score = 0; // 初始得分
        if (rule.cityCode.equals(ctx.cityCode())) { // 城市精确匹配
            score += 8; // 城市权重 8
        }
        if (rule.serviceItem.equals(ctx.serviceItem())) { // 服务项目精确匹配
            score += 4; // 服务项目权重 4
        }
        if (rule.category.equals(ctx.category())) { // 品类精确匹配
            score += 2; // 品类权重 2
        }
        if (rule.businessLine.equals(ctx.businessLine())) { // 业务线精确匹配
            score += 1; // 业务线权重 1
        }
        return score; // 返回总得分
    }

    /**
     * 根据分润模式计算分润金额。
     *
     * @param net        可分润净额
     * @param rule       分润规则
     * @param joinMonths 商户入驻月数
     * @return 分润金额
     */
    public BigDecimal calcShare(BigDecimal net, FeeShareRuleEntity rule, int joinMonths) {
        ShareMode mode = ShareMode.of(rule.shareMode); // 解析分润模式
        return switch (mode) { // 按模式分支计算
            case FIXED_RATE -> MoneyUtils.multiplyRate(net, rule.firstMonthValue); // 固定比例
            case FIXED_AMOUNT -> rule.firstMonthValue.min(net).setScale(MoneyUtils.MONEY_SCALE, MoneyUtils.ROUND); // 固定金额
            case STEP_DOWN -> MoneyUtils.multiplyRate(net, calcStepDown(rule, joinMonths)); // 阶梯递减
        };
    }

    /**
     * 计算阶梯递减模式下的有效分润比例。
     */
    public BigDecimal calcStepDown(FeeShareRuleEntity rule, int joinMonths) {
        BigDecimal current = rule.firstMonthValue // 首月比例
                .subtract(rule.stepDownVal.multiply(BigDecimal.valueOf(Math.max(0, joinMonths - 1)))); // 按入驻月数递减
        return current.compareTo(rule.minShare) < 0 ? rule.minShare : current; // 不低于最低分润比例
    }

    /**
     * 费用规则匹配上下文，携带各维度请求值。
     */
    public record FeeMatchContext(String businessLine, String category, String serviceItem, String cityCode) {
    }
}
