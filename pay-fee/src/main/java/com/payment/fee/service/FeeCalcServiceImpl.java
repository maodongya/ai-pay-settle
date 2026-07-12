package com.payment.fee.service; // 费用服务包

import com.payment.api.dto.FeeCalcDTO; // 费用计算请求 DTO
import com.payment.api.dto.FeeCalcResultDTO; // 费用计算结果 DTO
import com.payment.api.service.FeeCalcService; // 费用计算服务接口
import com.payment.common.util.MoneyUtils; // 金额工具类
import com.payment.domain.entity.FeeCalcResultEntity; // 费用计算结果实体
import com.payment.domain.entity.FeeShareRuleEntity; // 分润规则实体
import com.payment.domain.repository.FeeCalcResultRepository; // 费用计算结果仓储
import com.payment.domain.repository.FeeShareRuleRepository; // 分润规则仓储
import com.payment.fee.engine.FeeCalcPipeline; // 费用计算流水线
import org.springframework.stereotype.Service; // Spring 服务注解
import org.springframework.transaction.annotation.Transactional; // 事务注解

import java.math.BigDecimal; // 高精度数值
import java.math.RoundingMode; // 舍入模式
import java.time.LocalDateTime; // 本地日期时间
import java.util.List; // 列表

/**
 * 费用计算服务实现，提供正向分润与退款分润计算。
 */
@Service // 注册为 Spring 服务
public class FeeCalcServiceImpl implements FeeCalcService {

    private final FeeShareRuleRepository feeShareRuleRepository; // 分润规则仓储
    private final FeeCalcResultRepository feeCalcResultRepository; // 费用计算结果仓储
    private final FeeCalcPipeline feeCalcPipeline; // 费用计算流水线

    /**
     * 构造注入依赖。
     */
    public FeeCalcServiceImpl(FeeShareRuleRepository feeShareRuleRepository,
                              FeeCalcResultRepository feeCalcResultRepository,
                              FeeCalcPipeline feeCalcPipeline) {
        this.feeShareRuleRepository = feeShareRuleRepository; // 赋值规则仓储
        this.feeCalcResultRepository = feeCalcResultRepository; // 赋值结果仓储
        this.feeCalcPipeline = feeCalcPipeline; // 赋值计算流水线
    }

    /**
     * 计算正向交易分润费用，已存在则直接返回。
     */
    @Override // 实现接口方法
    @Transactional // 开启事务
    public FeeCalcResultDTO calcShareFee(FeeCalcDTO request) {
        return feeCalcResultRepository.findByBillNo(request.billNo) // 按账单号查询已有结果
                .map(this::toDto) // 存在则转为 DTO
                .orElseGet(() -> { // 不存在则执行计算
                    List<FeeShareRuleEntity> rules = feeShareRuleRepository.findAll(); // 加载全部规则
                    FeeCalcResultDTO result = feeCalcPipeline.execute(request, rules); // 执行流水线计算
                    persist(result); // 持久化结果
                    return result; // 返回计算结果
                });
    }

    /**
     * 计算退款分润费用，按原单比例等比冲减。
     */
    @Override // 实现接口方法
    @Transactional // 开启事务
    public FeeCalcResultDTO calcRefundFee(String originBillNo, String refundBillNo, BigDecimal refundAmount) {
        return feeCalcResultRepository.findByBillNo(refundBillNo) // 按退款单号查询已有结果
                .map(this::toDto) // 存在则转为 DTO
                .orElseGet(() -> { // 不存在则按比例计算
                    FeeCalcResultEntity origin = feeCalcResultRepository.findByBillNo(originBillNo) // 查询原单结果
                            .orElseThrow(() -> new IllegalArgumentException("origin not found")); // 原单不存在则异常
                    BigDecimal ratio = refundAmount.divide(origin.tradeAmount, 8, RoundingMode.HALF_UP); // 计算退款比例

                    FeeCalcResultDTO result = new FeeCalcResultDTO(); // 创建结果 DTO
                    result.billNo = refundBillNo; // 设置退款单号
                    result.merchantId = origin.merchantId; // 设置商户 ID
                    result.tradeAmount = refundAmount.negate(); // 设置负向交易金额
                    result.platformFee = scaleNeg(origin.platformFee, ratio); // 按比例冲减平台费
                    result.agentL1Share = scaleNeg(origin.agentL1Share, ratio); // 按比例冲减一级代理分润
                    result.agentL2Share = scaleNeg(origin.agentL2Share, ratio); // 按比例冲减二级代理分润
                    result.partnerShare = scaleNeg(origin.partnerShare, ratio); // 按比例冲减合作方分润
                    result.merchantIncome = scaleNeg(origin.merchantIncome, ratio); // 按比例冲减商户收入
                    result.ruleSnapshotJson = "{\"refundOrigin\":\"" + originBillNo + "\",\"ratio\":" + ratio + "}"; // 记录退款快照
                    persist(result); // 持久化结果
                    return result; // 返回计算结果
                });
    }

    /**
     * 按退款比例计算负向金额。
     */
    private BigDecimal scaleNeg(BigDecimal amount, BigDecimal ratio) {
        return MoneyUtils.money(amount.multiply(ratio)).negate(); // 乘比例后取负
    }

    /**
     * 将计算结果持久化到数据库。
     */
    private void persist(FeeCalcResultDTO result) {
        FeeCalcResultEntity entity = new FeeCalcResultEntity(); // 创建实体
        entity.billNo = result.billNo; // 账单号
        entity.merchantId = result.merchantId; // 商户 ID
        entity.tradeAmount = result.tradeAmount; // 交易金额
        entity.platformFee = result.platformFee; // 平台费
        entity.agentL1Share = result.agentL1Share; // 一级代理分润
        entity.agentL2Share = result.agentL2Share; // 二级代理分润
        entity.partnerShare = result.partnerShare; // 合作方分润
        entity.merchantIncome = result.merchantIncome; // 商户收入
        entity.ruleSnapshot = result.ruleSnapshotJson; // 规则快照 JSON
        entity.calcTime = LocalDateTime.now(); // 计算时间
        feeCalcResultRepository.save(entity); // 保存实体
    }

    /**
     * 将实体转换为 DTO。
     */
    private FeeCalcResultDTO toDto(FeeCalcResultEntity e) {
        FeeCalcResultDTO dto = new FeeCalcResultDTO(); // 创建 DTO
        dto.billNo = e.billNo; // 账单号
        dto.merchantId = e.merchantId; // 商户 ID
        dto.tradeAmount = e.tradeAmount; // 交易金额
        dto.platformFee = e.platformFee; // 平台费
        dto.agentL1Share = e.agentL1Share; // 一级代理分润
        dto.agentL2Share = e.agentL2Share; // 二级代理分润
        dto.partnerShare = e.partnerShare; // 合作方分润
        dto.merchantIncome = e.merchantIncome; // 商户收入
        dto.ruleSnapshotJson = e.ruleSnapshot; // 规则快照
        return dto; // 返回 DTO
    }
}
