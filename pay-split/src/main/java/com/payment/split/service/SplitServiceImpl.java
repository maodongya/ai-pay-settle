package com.payment.split.service; // 分账服务包

import com.baomidou.dynamic.datasource.annotation.DSTransactional;
import com.fasterxml.jackson.core.JsonProcessingException; // JSON 处理异常
import com.fasterxml.jackson.databind.ObjectMapper; // JSON 对象映射器
import com.payment.api.dto.AgentRelationDTO; // 代理关系 DTO
import com.payment.api.dto.FeeCalcResultDTO; // 费用计算结果 DTO
import com.payment.api.service.SplitService; // 分账服务接口
import com.payment.common.enums.Direction; // 借贷方向枚举
import com.payment.common.enums.PartyType; // 参与方类型枚举
import com.payment.common.metrics.PayBusinessMetrics; // 业务吞吐指标
import com.payment.domain.entity.AccountVoucherEntity; // 会计凭证实体
import com.payment.domain.entity.OutboxMessageEntity; // 发件箱消息实体
import com.payment.domain.entity.SplitDetailEntity; // 分账明细实体
import com.payment.domain.repository.AccountVoucherRepository; // 会计凭证仓储
import com.payment.domain.repository.OutboxMessageRepository; // 发件箱消息仓储
import com.payment.domain.repository.SplitDetailRepository; // 分账明细仓储
import com.payment.split.support.VoucherGenerator; // 凭证生成器
import org.springframework.stereotype.Service; // Spring 服务注解

import java.math.BigDecimal; // 高精度数值
import java.time.LocalDateTime; // 本地日期时间
import java.util.ArrayList; // 动态数组列表
import java.util.HashMap; // 哈希映射
import java.util.List; // 列表
import java.util.Map; // 映射

/**
 * 分账服务实现，生成分账明细、会计凭证和结算发件箱消息。
 */
@Service // 注册为 Spring 服务
public class SplitServiceImpl implements SplitService {

    private static final String SETTLE_TOPIC = "settle_amount_topic"; // 结算消息主题

    private final SplitDetailRepository splitDetailRepository; // 分账明细仓储
    private final AccountVoucherRepository accountVoucherRepository; // 会计凭证仓储
    private final OutboxMessageRepository outboxMessageRepository; // 发件箱消息仓储
    private final ObjectMapper objectMapper; // JSON 映射器
    private final PayBusinessMetrics businessMetrics; // 业务吞吐指标

    /**
     * 构造注入依赖。
     */
    public SplitServiceImpl(SplitDetailRepository splitDetailRepository,
                            AccountVoucherRepository accountVoucherRepository,
                            OutboxMessageRepository outboxMessageRepository,
                            ObjectMapper objectMapper,
                            PayBusinessMetrics businessMetrics) {
        this.splitDetailRepository = splitDetailRepository; // 赋值分账仓储
        this.accountVoucherRepository = accountVoucherRepository; // 赋值凭证仓储
        this.outboxMessageRepository = outboxMessageRepository; // 赋值发件箱仓储
        this.objectMapper = objectMapper; // 赋值 JSON 映射器
        this.businessMetrics = businessMetrics; // 赋值指标
    }

    /**
     * 根据费用计算结果生成分账明细及相关凭证。
     */
    @Override
    @DSTransactional
    public void generateSplitDetail(FeeCalcResultDTO calcResult, AgentRelationDTO relation) {
        if (splitDetailRepository.existsByBillNo(calcResult.billNo)) {
            ensureOutboxIfNeeded(calcResult);
            return;
        }

        List<SplitDetailEntity> details = buildDetails(calcResult, relation);
        splitDetailRepository.saveAll(details); // 批量保存明细

        if (!accountVoucherRepository.existsByBillNo(calcResult.billNo)) { // 凭证不存在
            List<AccountVoucherEntity> vouchers = VoucherGenerator.buildVouchers(calcResult); // 生成凭证
            accountVoucherRepository.saveAll(vouchers); // 批量保存凭证
        }

        if (calcResult.merchantIncome.compareTo(BigDecimal.ZERO) != 0) { // 商户收入非零
            OutboxMessageEntity outbox = new OutboxMessageEntity(); // 创建发件箱消息
            outbox.bizKey = calcResult.billNo; // 业务键
            outbox.merchantId = calcResult.merchantId; // 分片键
            outbox.topic = SETTLE_TOPIC; // 消息主题
            outbox.payload = buildSettlePayload(calcResult); // 构建载荷
            outbox.status = 0; // 待发送状态
            outbox.createTime = LocalDateTime.now(); // 创建时间
            outboxMessageRepository.save(outbox); // 保存消息
        }
        businessMetrics.recordSplitDone(calcResult.billNo, 0);
    }

    /** 分账已存在时仅补写缺失 Outbox，避免重试卡住结算 */
    private void ensureOutboxIfNeeded(FeeCalcResultDTO calcResult) {
        if (calcResult.merchantIncome == null || calcResult.merchantIncome.compareTo(BigDecimal.ZERO) == 0) {
            return;
        }
        if (outboxMessageRepository.existsByBizKey(calcResult.billNo)) {
            return;
        }
        OutboxMessageEntity outbox = new OutboxMessageEntity();
        outbox.bizKey = calcResult.billNo;
        outbox.merchantId = calcResult.merchantId;
        outbox.topic = SETTLE_TOPIC;
        outbox.payload = buildSettlePayload(calcResult);
        outbox.status = 0;
        outbox.createTime = LocalDateTime.now();
        outboxMessageRepository.save(outbox);
    }

    /**
     * 构建各参与方的分账明细列表。
     */
    private List<SplitDetailEntity> buildDetails(FeeCalcResultDTO r, AgentRelationDTO rel) {
        List<SplitDetailEntity> list = new ArrayList<>(); // 创建明细列表
        addPositive(list, r.billNo, r.merchantId, PartyType.PLATFORM, 0L, r.platformFee, Direction.RECEIVABLE); // 平台应收
        if (rel.agentId != null) { // 有一级代理
            addPositive(list, r.billNo, r.merchantId, PartyType.AGENT_L1, rel.agentId, r.agentL1Share, Direction.PAYABLE); // 一级代理应付
        }
        if (rel.secondAgentId != null) { // 有二级代理
            addPositive(list, r.billNo, r.merchantId, PartyType.AGENT_L2, rel.secondAgentId, r.agentL2Share, Direction.PAYABLE); // 二级代理应付
        }
        if (rel.splitPartyId != null) { // 有合作方
            addPositive(list, r.billNo, r.merchantId, PartyType.PARTNER, rel.splitPartyId, r.partnerShare, Direction.PAYABLE); // 合作方应付
        }
        addPositive(list, r.billNo, r.merchantId, PartyType.MERCHANT, r.merchantId, r.merchantIncome, Direction.PAYABLE); // 商户应付
        return list; // 返回明细列表
    }

    /**
     * 添加非零分账明细，负金额自动转为应收方向。
     */
    private void addPositive(List<SplitDetailEntity> list, String billNo, Long merchantId, PartyType type, Long partyId,
                             BigDecimal amount, Direction direction) {
        if (amount == null || amount.compareTo(BigDecimal.ZERO) == 0) { // 金额为空或零
            return; // 跳过
        }
        SplitDetailEntity detail = new SplitDetailEntity(); // 创建明细
        detail.billNo = billNo; // 账单号
        detail.merchantId = merchantId; // 分片键
        detail.partyType = type.getCode(); // 参与方类型
        detail.partyId = partyId; // 参与方 ID
        detail.amount = amount.abs(); // 取绝对值
        detail.direction = amount.signum() < 0 ? Direction.RECEIVABLE.getCode() : direction.getCode(); // 负金额转应收
        detail.createTime = LocalDateTime.now(); // 创建时间
        list.add(detail); // 加入列表
    }

    /**
     * 构建结算发件箱消息 JSON 载荷。
     */
    private String buildSettlePayload(FeeCalcResultDTO calcResult) {
        Map<String, Object> payload = new HashMap<>(); // 创建载荷映射
        payload.put("version", "1.0");
        payload.put("merchantId", calcResult.merchantId); // 商户 ID
        payload.put("billNo", calcResult.billNo); // 账单号
        payload.put("amount", calcResult.merchantIncome); // 商户收入金额
        payload.put("bizTime", LocalDateTime.now().toString()); // 业务时间
        try { // 序列化为 JSON
            return objectMapper.writeValueAsString(payload); // 返回 JSON 字符串
        } catch (JsonProcessingException e) { // JSON 序列化失败
            throw new IllegalStateException(e); // 包装为运行时异常
        }
    }
}
