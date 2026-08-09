package com.payment.fee.service;

import com.payment.api.dto.FeeCalcDTO;
import com.payment.api.dto.FeeCalcResultDTO;
import com.payment.api.service.FeeCalcService;
import com.payment.common.util.MoneyUtils;
import com.payment.domain.entity.FeeCalcResultEntity;
import com.payment.domain.repository.FeeCalcResultRepository;
import com.payment.domain.repository.FeeShareRuleRepository;
import com.payment.fee.engine.FeeCalcPipeline;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDateTime;
import java.util.List;

/**
 * 费用计算：幂等靠 billNo 唯一键；不加 Redis 锁、不开独立事务（由清算 TxSupport 持有）。
 */
@Service
public class FeeCalcServiceImpl implements FeeCalcService {

    private final FeeShareRuleRepository feeShareRuleRepository;
    private final FeeCalcResultRepository feeCalcResultRepository;
    private final FeeCalcPipeline feeCalcPipeline;

    public FeeCalcServiceImpl(FeeShareRuleRepository feeShareRuleRepository,
                              FeeCalcResultRepository feeCalcResultRepository,
                              FeeCalcPipeline feeCalcPipeline) {
        this.feeShareRuleRepository = feeShareRuleRepository;
        this.feeCalcResultRepository = feeCalcResultRepository;
        this.feeCalcPipeline = feeCalcPipeline;
    }

    @Override
    public FeeCalcResultDTO calcShareFee(FeeCalcDTO request) {
        return feeCalcResultRepository.findByBillNo(request.billNo)
                .map(this::toDto)
                .orElseGet(() -> calcAndPersistShare(request));
    }

    /** C5：入口已查过一次；此处直接算费，并发冲突靠唯一键 + 再读 */
    private FeeCalcResultDTO calcAndPersistShare(FeeCalcDTO request) {
        List<com.payment.domain.entity.FeeShareRuleEntity> rules = feeShareRuleRepository.findAll();
        FeeCalcResultDTO result = feeCalcPipeline.execute(request, rules);
        persistIdempotent(result);
        return feeCalcResultRepository.findByBillNo(request.billNo)
                .map(this::toDto)
                .orElse(result);
    }

    @Override
    public FeeCalcResultDTO calcRefundFee(String originBillNo, String refundBillNo, BigDecimal refundAmount) {
        return feeCalcResultRepository.findByBillNo(refundBillNo)
                .map(this::toDto)
                .orElseGet(() -> {
                    FeeCalcResultEntity origin = feeCalcResultRepository.findByBillNo(originBillNo)
                            .orElseThrow(() -> new IllegalArgumentException("origin not found"));
                    BigDecimal ratio = refundAmount.divide(origin.tradeAmount, 8, RoundingMode.HALF_UP);

                    FeeCalcResultDTO result = new FeeCalcResultDTO();
                    result.billNo = refundBillNo;
                    result.merchantId = origin.merchantId;
                    result.tradeAmount = refundAmount.negate();
                    result.platformFee = scaleNeg(origin.platformFee, ratio);
                    result.agentL1Share = scaleNeg(origin.agentL1Share, ratio);
                    result.agentL2Share = scaleNeg(origin.agentL2Share, ratio);
                    result.partnerShare = scaleNeg(origin.partnerShare, ratio);
                    result.merchantIncome = scaleNeg(origin.merchantIncome, ratio);
                    result.ruleSnapshotJson = "{\"refundOrigin\":\"" + originBillNo + "\",\"ratio\":" + ratio + "}";
                    persistIdempotent(result);
                    return feeCalcResultRepository.findByBillNo(refundBillNo)
                            .map(this::toDto)
                            .orElse(result);
                });
    }

    private BigDecimal scaleNeg(BigDecimal amount, BigDecimal ratio) {
        return MoneyUtils.money(amount.multiply(ratio)).negate();
    }

    private void persistIdempotent(FeeCalcResultDTO result) {
        try {
            FeeCalcResultEntity entity = new FeeCalcResultEntity();
            entity.billNo = result.billNo;
            entity.merchantId = result.merchantId;
            entity.tradeAmount = result.tradeAmount;
            entity.platformFee = result.platformFee;
            entity.agentL1Share = result.agentL1Share;
            entity.agentL2Share = result.agentL2Share;
            entity.partnerShare = result.partnerShare;
            entity.merchantIncome = result.merchantIncome;
            entity.ruleSnapshot = result.ruleSnapshotJson;
            entity.calcTime = LocalDateTime.now();
            feeCalcResultRepository.save(entity);
        } catch (DataIntegrityViolationException ignored) {
            // 并发下唯一键冲突：读已有结果即可
        }
    }

    private FeeCalcResultDTO toDto(FeeCalcResultEntity e) {
        FeeCalcResultDTO dto = new FeeCalcResultDTO();
        dto.billNo = e.billNo;
        dto.merchantId = e.merchantId;
        dto.tradeAmount = e.tradeAmount;
        dto.platformFee = e.platformFee;
        dto.agentL1Share = e.agentL1Share;
        dto.agentL2Share = e.agentL2Share;
        dto.partnerShare = e.partnerShare;
        dto.merchantIncome = e.merchantIncome;
        dto.ruleSnapshotJson = e.ruleSnapshot;
        return dto;
    }
}
