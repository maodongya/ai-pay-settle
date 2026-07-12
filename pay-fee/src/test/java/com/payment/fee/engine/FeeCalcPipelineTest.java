package com.payment.fee.engine; // 费用引擎测试包

import com.payment.api.dto.FeeCalcDTO; // 费用计算请求 DTO
import com.payment.api.dto.FeeCalcResultDTO; // 费用计算结果 DTO
import com.payment.common.enums.ShareMode; // 分润模式枚举
import com.payment.common.enums.TargetType; // 分润目标类型枚举
import com.payment.domain.entity.FeeShareRuleEntity; // 分润规则实体
import com.payment.domain.entity.MerchantContractEntity; // 商户合约实体
import com.payment.domain.repository.MerchantContractRepository; // 商户合约仓储
import org.junit.jupiter.api.BeforeEach; // 测试前置注解
import org.junit.jupiter.api.Test; // 测试方法注解

import java.lang.reflect.Proxy; // 动态代理
import java.math.BigDecimal; // 高精度数值
import java.time.LocalDate; // 本地日期
import java.time.LocalDateTime; // 本地日期时间
import java.util.List; // 列表
import java.util.Optional; // 可选值

import static org.junit.jupiter.api.Assertions.assertEquals; // 断言相等

/**
 * 费用计算流水线单元测试。
 */
class FeeCalcPipelineTest {

    private FeeCalcPipeline pipeline; // 被测流水线
    private List<FeeShareRuleEntity> rules; // 测试规则列表

    /**
     * 每个测试方法执行前初始化流水线与规则。
     */
    @BeforeEach // 测试前置
    void setUp() {
        MerchantContractRepository contractRepo = stubContractRepo(); // 桩合约仓储
        pipeline = new FeeCalcPipeline(new RuleMatcher(), contractRepo); // 创建流水线
        rules = List.of( // 构建测试规则
                rule(1L, TargetType.PLATFORM, ShareMode.FIXED_RATE, "0.0060"), // 平台 0.6%
                rule(2L, TargetType.AGENT_L1, ShareMode.FIXED_RATE, "0.0500"), // 一级代理 5%
                rule(3L, TargetType.AGENT_L2, ShareMode.FIXED_RATE, "0.0300"), // 二级代理 3%
                rule(4L, TargetType.PARTNER, ShareMode.STEP_DOWN, "0.0200") // 合作方阶梯递减
        );
    }

    /**
     * 验证完整分润流水线各角色金额计算正确。
     */
    @Test // 测试方法
    void shouldCalculateFullPipeline() {
        FeeCalcDTO req = new FeeCalcDTO(); // 创建请求
        req.billNo = "CL20260705000001"; // 账单号
        req.merchantId = 100001L; // 商户 ID
        req.agentId = 200001L; // 一级代理 ID
        req.secondAgentId = 200002L; // 二级代理 ID
        req.splitPartyId = 300001L; // 合作方 ID
        req.tradeAmount = new BigDecimal("1000.00"); // 交易金额
        req.businessLine = "A"; // 业务线
        req.category = "A01"; // 品类
        req.serviceItem = "A0101"; // 服务项目
        req.cityCode = "110000"; // 城市编码

        FeeCalcResultDTO result = pipeline.execute(req, rules); // 执行计算

        assertEquals(new BigDecimal("6.00"), result.platformFee); // 断言平台费
        assertEquals(new BigDecimal("49.70"), result.agentL1Share); // 断言一级代理分润
        assertEquals(new BigDecimal("28.33"), result.agentL2Share); // 断言二级代理分润
        assertEquals(new BigDecimal("10.08"), result.partnerShare); // 断言合作方分润
        assertEquals(new BigDecimal("905.89"), result.merchantIncome); // 断言商户收入
    }

    /**
     * 创建商户合约仓储的动态代理桩。
     */
    private static MerchantContractRepository stubContractRepo() {
        return (MerchantContractRepository) Proxy.newProxyInstance( // 创建动态代理
                MerchantContractRepository.class.getClassLoader(), // 类加载器
                new Class[]{MerchantContractRepository.class}, // 代理接口
                (proxy, method, args) -> { // 方法拦截
                    if ("findByMerchantId".equals(method.getName())) { // 按商户 ID 查询
                        MerchantContractEntity c = new MerchantContractEntity(); // 创建合约
                        c.merchantId = (Long) args[0]; // 设置商户 ID
                        c.signDate = LocalDate.of(2025, 9, 15); // 设置签约日期
                        return Optional.of(c); // 返回可选合约
                    }
                    Class<?> returnType = method.getReturnType(); // 获取返回类型
                    if (returnType.equals(boolean.class)) { // 布尔类型
                        return false; // 默认 false
                    }
                    if (returnType.isPrimitive()) { // 其他基本类型
                        return 0; // 默认 0
                    }
                    return null; // 引用类型默认 null
                });
    }

    /**
     * 构建测试用分润规则实体。
     */
    private FeeShareRuleEntity rule(Long id, TargetType target, ShareMode mode, String value) {
        FeeShareRuleEntity r = new FeeShareRuleEntity(); // 创建规则
        r.ruleId = id; // 规则 ID
        r.targetType = target.getCode(); // 目标类型
        r.businessLine = "*"; // 业务线通配
        r.category = "*"; // 品类通配
        r.serviceItem = "*"; // 服务项目通配
        r.cityCode = "*"; // 城市通配
        r.shareMode = mode.getCode(); // 分润模式
        r.firstMonthValue = new BigDecimal(value); // 首月值
        r.stepDownVal = new BigDecimal("0.0010"); // 递减步长
        r.minShare = new BigDecimal("0.0050"); // 最低分润
        r.validStart = LocalDateTime.of(2026, 1, 1, 0, 0); // 生效时间
        r.status = 1; // 启用状态
        return r; // 返回规则
    }
}
