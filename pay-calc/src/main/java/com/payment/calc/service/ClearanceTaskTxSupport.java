package com.payment.calc.service;

import com.baomidou.dynamic.datasource.annotation.DSTransactional;
import com.payment.api.dto.AgentRelationDTO;
import com.payment.api.dto.FeeCalcDTO;
import com.payment.api.dto.FeeCalcResultDTO;
import com.payment.api.service.FeeCalcService;
import com.payment.api.service.SplitService;
import com.payment.common.enums.BillStatus;
import com.payment.common.enums.BillType;
import com.payment.common.enums.TaskStatus;
import com.payment.domain.entity.TradeBillEntity;
import com.payment.domain.repository.ClearanceTaskRepository;
import com.payment.domain.repository.TradeBillRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.Optional;

/**
 * 清算分阶段事务：缩短单次持连时间；工单/告警不在本类内执行。
 * 写路径锁序统一：先 task，后 bill（C4）。
 */
@Component
public class ClearanceTaskTxSupport {

    private static final Logger log = LoggerFactory.getLogger(ClearanceTaskTxSupport.class);
    private static final int MAX_RETRY = 5;
    private static final int ERROR_MSG_MAX = 500;

    private final ClearanceTaskRepository clearanceTaskRepository;
    private final TradeBillRepository tradeBillRepository;
    private final FeeCalcService feeCalcService;
    private final SplitService splitService;

    public ClearanceTaskTxSupport(ClearanceTaskRepository clearanceTaskRepository,
                                  TradeBillRepository tradeBillRepository,
                                  FeeCalcService feeCalcService,
                                  SplitService splitService) {
        this.clearanceTaskRepository = clearanceTaskRepository;
        this.tradeBillRepository = tradeBillRepository;
        this.feeCalcService = feeCalcService;
        this.splitService = splitService;
    }

    @DSTransactional
    public Optional<ClearanceClaimContext> claimAndMarkClearing(String billNo, Long merchantId) {
        int claimed = clearanceTaskRepository.claimTask(
                billNo, merchantId, TaskStatus.PENDING.getCode(), TaskStatus.RUNNING.getCode(), LocalDateTime.now());
        if (claimed == 0) {
            return Optional.empty();
        }
        Optional<TradeBillEntity> billOpt = tradeBillRepository.findByBillNoAndMerchantId(billNo, merchantId);
        if (billOpt.isEmpty()) {
            markTaskDead(billNo, merchantId, "trade bill not found");
            return Optional.empty();
        }
        TradeBillEntity bill = billOpt.get();
        int billUpdated = tradeBillRepository.updateStatusByBillNoAndMerchantId(
                billNo, merchantId, BillStatus.PENDING.getCode(), BillStatus.CLEARING.getCode());
        if (billUpdated == 0) {
            // C3-b：CAS miss 时最多再读一次状态；已 CLEARING 则复用已读实体，禁止第三跳
            int currentStatus = tradeBillRepository.findByBillNoAndMerchantId(billNo, merchantId)
                    .map(b -> {
                        bill.status = b.status;
                        return b.status;
                    })
                    .orElse(-1);
            if (currentStatus != BillStatus.CLEARING.getCode()) {
                throw new IllegalStateException("bill not ready for clearing billNo=" + billNo);
            }
        } else {
            bill.status = BillStatus.CLEARING.getCode();
        }
        return Optional.of(new ClearanceClaimContext(bill, merchantId));
    }

    @DSTransactional
    public FeeCalcResultDTO runFeeAndSplit(TradeBillEntity bill, AgentRelationDTO relation) {
        FeeCalcResultDTO feeResult = runFeeCalc(bill, relation);
        splitService.generateSplitDetail(feeResult, relation);
        return feeResult;
    }

    @DSTransactional
    public FeeCalcResultDTO runFeeCalc(TradeBillEntity bill, AgentRelationDTO relation) {
        String billNo = bill.billNo;
        if (bill.billType == BillType.REFUND.getCode()) {
            return feeCalcService.calcRefundFee(bill.originBillNo, billNo, bill.tradeAmount);
        }
        FeeCalcDTO req = new FeeCalcDTO();
        req.billNo = billNo;
        req.merchantId = bill.merchantId;
        req.agentId = relation.agentId != null ? relation.agentId : bill.agentId;
        req.secondAgentId = relation.secondAgentId != null ? relation.secondAgentId : bill.secondAgentId;
        req.splitPartyId = relation.splitPartyId;
        req.tradeAmount = bill.tradeAmount;
        req.businessLine = bill.businessLine;
        req.category = bill.category;
        req.serviceItem = bill.serviceItem;
        req.cityCode = bill.cityCode;
        return feeCalcService.calcShareFee(req);
    }

    @DSTransactional
    public void runSplitDetail(FeeCalcResultDTO result, AgentRelationDTO relation) {
        splitService.generateSplitDetail(result, relation);
    }

    /**
     * 成功落态：先 task RUNNING→SUCCESS，再 bill CLEARING→CLEARED（与 claim 锁序一致，同事务回滚）。
     */
    @DSTransactional
    public void finalizeSuccess(ClearanceClaimContext ctx) {
        String billNo = ctx.bill().billNo;
        Long merchantId = ctx.merchantId();
        int taskUpdated = clearanceTaskRepository.markSuccess(
                billNo, merchantId, TaskStatus.RUNNING.getCode(), TaskStatus.SUCCESS.getCode(), LocalDateTime.now());
        if (taskUpdated == 0) {
            throw new IllegalStateException("finalize task status mismatch billNo=" + billNo);
        }
        int billUpdated = tradeBillRepository.updateStatusByBillNoAndMerchantId(
                billNo, merchantId, BillStatus.CLEARING.getCode(), BillStatus.CLEARED.getCode());
        if (billUpdated == 0) {
            throw new IllegalStateException("finalize bill status mismatch billNo=" + billNo);
        }
    }

    /**
     * 失败处理：单 SQL 更新任务 FAILED/DEAD 与账单 FAILED。工单/告警由调用方事务外处理。
     */
    @DSTransactional
    public ClearanceFailureOutcome markFailure(ClearanceClaimContext ctx, Exception e) {
        return failRunningTask(ctx.bill().billNo, ctx.merchantId(), e.getMessage());
    }

    /**
     * Watchdog：RUNNING 超时任务与 CLEARING 账单同短事务失败化。
     */
    @DSTransactional
    public ClearanceFailureOutcome watchdogFail(String billNo, Long merchantId) {
        return failRunningTask(billNo, merchantId, "watchdog timeout");
    }

    /**
     * C3：不再为取 retryCount 而 find 整行；markFailed SQL 原子 +1；enteredDead 仅在 updated 后轻量查 status。
     * R8：bill CAS 更新行数为 0 时打告警（task 已终态、bill 可能非 CLEARING）。
     */
    private ClearanceFailureOutcome failRunningTask(String billNo, Long merchantId, String rawError) {
        String errorMsg = truncateError(rawError);
        LocalDateTime now = LocalDateTime.now();
        int updated = clearanceTaskRepository.markFailed(
                billNo, merchantId, TaskStatus.RUNNING.getCode(),
                TaskStatus.FAILED.getCode(), TaskStatus.DEAD.getCode(),
                MAX_RETRY, errorMsg, now);
        if (updated == 0) {
            return new ClearanceFailureOutcome(billNo, errorMsg, false, false);
        }
        boolean enteredDead = clearanceTaskRepository.findStatusByBillNoAndMerchantId(billNo, merchantId)
                .map(status -> status == TaskStatus.DEAD.getCode())
                .orElse(false);
        int billUpdated = tradeBillRepository.updateStatusByBillNoAndMerchantId(
                billNo, merchantId, BillStatus.CLEARING.getCode(), BillStatus.FAILED.getCode());
        if (billUpdated == 0) {
            log.warn("fail bill status mismatch billNo={} merchantId={} (task marked, bill not CLEARING)",
                    billNo, merchantId);
        }
        return new ClearanceFailureOutcome(billNo, errorMsg, enteredDead, true);
    }

    /**
     * 账单缺失等场景：将任务标 DEAD。工单由调用方在事务外补齐。
     */
    @DSTransactional
    public boolean markTaskDead(String billNo, Long merchantId, String reason) {
        return clearanceTaskRepository.markDead(
                billNo, merchantId, TaskStatus.DEAD.getCode(), truncateError(reason), LocalDateTime.now()) > 0;
    }

    private static String truncateError(String msg) {
        if (msg == null) {
            return null;
        }
        return msg.length() <= ERROR_MSG_MAX ? msg : msg.substring(0, ERROR_MSG_MAX);
    }
}
