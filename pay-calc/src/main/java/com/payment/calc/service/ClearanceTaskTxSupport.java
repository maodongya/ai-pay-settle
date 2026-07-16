package com.payment.calc.service; // 清算服务包

import com.baomidou.dynamic.datasource.annotation.DSTransactional; // 多数据源事务
import com.payment.api.dto.AgentRelationDTO; // 代理关系 DTO
import com.payment.api.dto.FeeCalcDTO; // 费用计算请求 DTO
import com.payment.api.dto.FeeCalcResultDTO; // 费用计算结果 DTO
import com.payment.api.service.FeeCalcService; // 费用计算服务
import com.payment.api.service.SplitService; // 分账服务
import com.payment.common.enums.BillStatus; // 账单状态枚举
import com.payment.common.enums.BillType; // 账单类型枚举
import com.payment.common.enums.TaskStatus; // 任务状态枚举
import com.payment.control.service.AlertService; // 告警服务
import com.payment.control.service.ExceptionRecordService; // 异常工单服务
import com.payment.domain.entity.TradeBillEntity; // 交易账单实体
import com.payment.domain.repository.ClearanceTaskRepository; // 清算任务仓储
import com.payment.domain.repository.TradeBillRepository; // 交易账单仓储
import org.springframework.stereotype.Component; // Spring 组件

import java.time.Duration; // 时间间隔
import java.time.LocalDateTime; // 本地日期时间
import java.util.Optional; // Optional 容器

/**
 * 清算分阶段事务：缩短单次持连时间，降低 calc 消费与连接池竞争。
 */
@Component // 独立 Bean，保证 @DSTransactional 代理生效
public class ClearanceTaskTxSupport {

    private static final int MAX_RETRY = 5; // 最大业务重试次数
    private static final int ERROR_MSG_MAX = 500; // error_msg 列最大长度余量

    private final ClearanceTaskRepository clearanceTaskRepository; // 清算任务仓储
    private final TradeBillRepository tradeBillRepository; // 交易账单仓储
    private final FeeCalcService feeCalcService; // 费用计算服务
    private final SplitService splitService; // 分账服务
    private final ExceptionRecordService exceptionRecordService; // 异常工单
    private final AlertService alertService; // 告警

    /**
     * 构造注入各阶段依赖。
     */
    public ClearanceTaskTxSupport(ClearanceTaskRepository clearanceTaskRepository,
                                  TradeBillRepository tradeBillRepository,
                                  FeeCalcService feeCalcService,
                                  SplitService splitService,
                                  ExceptionRecordService exceptionRecordService,
                                  AlertService alertService) {
        this.clearanceTaskRepository = clearanceTaskRepository; // 赋值任务仓储
        this.tradeBillRepository = tradeBillRepository; // 赋值账单仓储
        this.feeCalcService = feeCalcService; // 赋值计费服务
        this.splitService = splitService; // 赋值分账服务
        this.exceptionRecordService = exceptionRecordService; // 赋值工单服务
        this.alertService = alertService; // 赋值告警服务
    }

    /**
     * 阶段 1：抢占任务并标记账单 CLEARING（短事务）；返回 claim 上下文供后续阶段精确路由。
     */
    @DSTransactional // 多数据源短事务
    public Optional<ClearanceClaimContext> claimAndMarkClearing(String billNo, Long merchantId) {
        int claimed = clearanceTaskRepository.claimTask( // CAS 抢占 PENDING→RUNNING
                billNo, merchantId, TaskStatus.PENDING.getCode(), TaskStatus.RUNNING.getCode(), LocalDateTime.now());
        if (claimed == 0) { // 抢占失败（幂等/并发）
            return Optional.empty(); // 返回空，由上层 ACK
        }
        Optional<TradeBillEntity> billOpt = tradeBillRepository.findByBillNoAndMerchantId(billNo, merchantId); // 按 merchantId 路由
        if (billOpt.isEmpty()) { // 账单不存在
            markTaskDead(billNo, merchantId, "trade bill not found"); // 标 DEAD
            return Optional.empty(); // 结束
        }
        TradeBillEntity bill = billOpt.get(); // 取出账单
        int billUpdated = tradeBillRepository.updateStatusByBillNoAndMerchantId( // 单 SQL 标 CLEARING
                billNo, merchantId, BillStatus.PENDING.getCode(), BillStatus.CLEARING.getCode());
        if (billUpdated == 0) { // 可能已被其他线程标为 CLEARING
            int currentStatus = tradeBillRepository.findByBillNoAndMerchantId(billNo, merchantId)
                    .map(b -> b.status).orElse(-1);
            if (currentStatus != BillStatus.CLEARING.getCode()) {
                throw new IllegalStateException("bill not ready for clearing billNo=" + billNo);
            }
        }
        bill.status = BillStatus.CLEARING.getCode(); // 内存态与 DB 对齐
        return Optional.of(new ClearanceClaimContext(bill, merchantId)); // 携带 merchantId 上下文
    }

    /**
     * 阶段 2：计费 + 分账 + outbox（主事务，不含前后状态查询）。
     */
    @DSTransactional // 多数据源主事务
    public void runFeeAndSplit(TradeBillEntity bill, AgentRelationDTO relation) {
        String billNo = bill.billNo; // 账单号
        FeeCalcResultDTO result; // 计费结果
        if (bill.billType == BillType.REFUND.getCode()) { // 退款单
            result = feeCalcService.calcRefundFee(bill.originBillNo, billNo, bill.tradeAmount); // 退费用
        } else { // 正向交易
            FeeCalcDTO req = new FeeCalcDTO(); // 构建计费请求
            req.billNo = billNo; // 账单号
            req.merchantId = bill.merchantId; // 商户 ID
            req.agentId = relation.agentId != null ? relation.agentId : bill.agentId; // 一级代理
            req.secondAgentId = relation.secondAgentId != null ? relation.secondAgentId : bill.secondAgentId; // 二级代理
            req.splitPartyId = relation.splitPartyId; // 合作方
            req.tradeAmount = bill.tradeAmount; // 交易金额
            req.businessLine = bill.businessLine; // 业务线
            req.category = bill.category; // 品类
            req.serviceItem = bill.serviceItem; // 服务项目
            req.cityCode = bill.cityCode; // 城市
            result = feeCalcService.calcShareFee(req); // 正向分润
        }
        splitService.generateSplitDetail(result, relation); // 分账明细+凭证+outbox
    }

    /**
     * 阶段 3：单 SQL 标记账单 CLEARED、任务 SUCCESS（短事务，merchantId 精确路由）。
     */
    @DSTransactional // 多数据源短事务
    public void finalizeSuccess(ClearanceClaimContext ctx) {
        String billNo = ctx.bill().billNo; // 账单号
        Long merchantId = ctx.merchantId(); // 分片键
        int billUpdated = tradeBillRepository.updateStatusByBillNoAndMerchantId( // PR-C3：单 SQL 更新账单
                billNo, merchantId, BillStatus.CLEARING.getCode(), BillStatus.CLEARED.getCode());
        if (billUpdated == 0) { // 状态不匹配
            throw new IllegalStateException("finalize bill status mismatch billNo=" + billNo); // 明确失败
        }
        int taskUpdated = clearanceTaskRepository.markSuccess( // PR-C3：单 SQL 更新任务
                billNo, merchantId, TaskStatus.RUNNING.getCode(), TaskStatus.SUCCESS.getCode(), LocalDateTime.now());
        if (taskUpdated == 0) { // 任务非 RUNNING
            throw new IllegalStateException("finalize task status mismatch billNo=" + billNo); // 明确失败
        }
    }

    /**
     * 失败处理：单 SQL 更新任务 FAILED/DEAD 与账单 FAILED（merchantId 精确路由）。
     */
    @DSTransactional // 多数据源短事务
    public void markFailure(ClearanceClaimContext ctx, Exception e) {
        String billNo = ctx.bill().billNo; // 账单号
        Long merchantId = ctx.merchantId(); // 分片键
        int retryCount = clearanceTaskRepository.findByBillNoAndMerchantId(billNo, merchantId)
                .map(task -> task.retryCount).orElse(0); // 当前重试次数
        LocalDateTime nextRetry = LocalDateTime.now().plus(backoffDuration(retryCount + 1)); // 退避时间
        String errorMsg = truncateError(e.getMessage()); // 截断错误
        int updated = clearanceTaskRepository.markFailed( // 单 SQL 递增 retry 并标 FAILED/DEAD
                billNo, merchantId, TaskStatus.RUNNING.getCode(),
                TaskStatus.FAILED.getCode(), TaskStatus.DEAD.getCode(),
                MAX_RETRY, errorMsg, nextRetry, LocalDateTime.now());
        if (updated == 0) { // 任务非 RUNNING
            throw new IllegalStateException("markFailure task status mismatch billNo=" + billNo);
        }
        if (retryCount + 1 >= MAX_RETRY) { // 刚进入 DEAD
            exceptionRecordService.openClearanceDead(billNo, errorMsg); // 建工单
            alertService.send(AlertService.CLEARANCE_DEAD, // P2 告警
                    "billNo=" + billNo + " err=" + errorMsg); // 告警内容
        }
        tradeBillRepository.updateStatusByBillNoAndMerchantId( // 账单标 FAILED
                billNo, merchantId, BillStatus.CLEARING.getCode(), BillStatus.FAILED.getCode());
    }

    /**
     * 账单缺失等场景：将任务标 DEAD（merchantId 精确路由）。
     */
    @DSTransactional // 多数据源短事务
    public void markTaskDead(String billNo, Long merchantId, String reason) {
        int updated = clearanceTaskRepository.markDead( // 单 SQL 标 DEAD
                billNo, merchantId, TaskStatus.DEAD.getCode(), truncateError(reason), LocalDateTime.now());
        if (updated > 0) { // 更新成功
            exceptionRecordService.openClearanceDead(billNo, truncateError(reason)); // 建工单
        }
    }

    /** 截断错误信息，避免超出 VARCHAR(512) */
    private static String truncateError(String msg) {
        if (msg == null) { // 空消息
            return null; // 返回 null
        }
        return msg.length() <= ERROR_MSG_MAX ? msg : msg.substring(0, ERROR_MSG_MAX); // 截断
    }

    /** 业务重试退避：1/5/15/30/60 分钟 */
    private static Duration backoffDuration(int retryCount) {
        return switch (retryCount) { // 按次数阶梯
            case 1 -> Duration.ofMinutes(1); // 第 1 次 +1min
            case 2 -> Duration.ofMinutes(5); // 第 2 次 +5min
            case 3 -> Duration.ofMinutes(15); // 第 3 次 +15min
            case 4 -> Duration.ofMinutes(30); // 第 4 次 +30min
            default -> Duration.ofMinutes(60); // 第 5 次 +60min
        };
    }
}
