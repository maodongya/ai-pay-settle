package com.payment.calc.service; // 清算计算服务包

import com.payment.api.dto.AgentRelationDTO; // 代理关系 DTO
import com.payment.api.dto.FeeCalcResultDTO; // 费用计算结果 DTO
import com.payment.api.service.ClearanceTaskService; // 清算任务服务接口
import com.payment.api.service.MerchantValidateService; // 商户校验服务接口
import com.payment.calc.metrics.ClearanceTaskMetrics; // calc 清算监控指标
import com.payment.calc.support.ClearanceTaskPublisher; // 清算 MQ 发布器
import com.payment.common.enums.BillStatus; // 账单状态枚举
import com.payment.common.enums.TaskStatus; // 任务状态枚举
import com.payment.common.shard.ShardRouter;
import com.payment.common.metrics.PayBusinessMetrics; // 业务吞吐指标
import com.payment.common.ratelimit.DbRateLimit;
import com.payment.common.ratelimit.DbRateLimitLayer;
import com.payment.control.service.AlertService;
import com.payment.control.service.ExceptionRecordService;
import com.payment.domain.entity.ClearanceTaskEntity; // 清算任务实体
import com.payment.domain.entity.TradeBillEntity; // 交易账单实体
import com.payment.domain.repository.ClearanceTaskRepository; // 清算任务仓储
import com.payment.domain.repository.TradeBillRepository; // 交易账单仓储
import com.payment.domain.service.ShardRouteService; // 分片路由
import com.payment.mq.config.PayMqProperties; // MQ 开关
import com.payment.mq.exception.NonRetryableException; // 不可 MQ 重试异常
import org.slf4j.Logger; // 日志接口
import org.slf4j.LoggerFactory; // 日志工厂
import com.payment.domain.support.ShardScanSupport;
import java.time.LocalDateTime; // 本地日期时间
import java.util.List; // 列表
import java.util.Optional; // Optional
import org.springframework.stereotype.Service; // Spring 服务注解

/**
 * 清算任务服务实现，负责创建、执行和重试清算任务。
 */
@Service // 注册为 Spring 服务
public class ClearanceTaskServiceImpl implements ClearanceTaskService {

    private static final Logger log = LoggerFactory.getLogger(ClearanceTaskServiceImpl.class); // 日志记录器
    private static final int MAX_RETRY = 5; // 最大业务重试次数（L2）

    private final ClearanceTaskRepository clearanceTaskRepository; // 清算任务仓储
    private final TradeBillRepository tradeBillRepository; // 交易账单仓储
    private final ShardRouteService shardRouteService; // bill_no → merchant_id
    private final MerchantValidateService merchantValidateService; // 商户校验服务
    private final PayMqProperties payMqProperties; // MQ 模式开关
    private final ClearanceTaskPublisher clearanceTaskPublisher; // 清算 MQ 发布
    private final PayBusinessMetrics businessMetrics; // 业务吞吐指标
    private final ClearanceTaskTxSupport clearanceTaskTxSupport; // 分阶段事务
    private final ClearanceTaskMetrics clearanceTaskMetrics; // calc 监控
    private final ExceptionRecordService exceptionRecordService;
    private final AlertService alertService;

    /**
     * 构造注入依赖。
     */
    public ClearanceTaskServiceImpl(ClearanceTaskRepository clearanceTaskRepository,
                                    TradeBillRepository tradeBillRepository,
                                    ShardRouteService shardRouteService,
                                    MerchantValidateService merchantValidateService,
                                    PayMqProperties payMqProperties,
                                    ClearanceTaskPublisher clearanceTaskPublisher,
                                    PayBusinessMetrics businessMetrics,
                                    ClearanceTaskTxSupport clearanceTaskTxSupport,
                                    ClearanceTaskMetrics clearanceTaskMetrics,
                                    ExceptionRecordService exceptionRecordService,
                                    AlertService alertService) {
        this.clearanceTaskRepository = clearanceTaskRepository; // 赋值任务仓储
        this.tradeBillRepository = tradeBillRepository; // 赋值账单仓储
        this.shardRouteService = shardRouteService; // 赋值路由服务
        this.merchantValidateService = merchantValidateService; // 赋值校验服务
        this.payMqProperties = payMqProperties; // 赋值 MQ 配置
        this.clearanceTaskPublisher = clearanceTaskPublisher; // 赋值发布器
        this.businessMetrics = businessMetrics; // 赋值指标
        this.clearanceTaskTxSupport = clearanceTaskTxSupport; // 赋值分阶段事务
        this.clearanceTaskMetrics = clearanceTaskMetrics; // 赋值 calc 指标
        this.exceptionRecordService = exceptionRecordService;
        this.alertService = alertService;
    }

    /**
     * 创建清算任务，已存在则跳过。
     */
    @Override
    @DbRateLimit(layer = DbRateLimitLayer.CALC)
    public void createTask(String billNo, Long merchantId) {
        clearanceTaskRepository.insertIfAbsent(
                billNo, merchantId, ShardRouter.shardId(merchantId),
                TaskStatus.PENDING.getCode(), LocalDateTime.now());
    }

    /**
     * 执行清算任务：计费→分账→更新状态；协调 MQ L1 与业务 L2 重试。
     */
    @Override // 实现接口方法
    public void executeTask(String billNo) {
        executeTask(billNo, null); // 委托带 merchantId 的重载
    }

    /**
     * 执行清算任务（带 merchantId 分片键，避免 bill_route 跨库不可见）。
     */
    @Override // 实现接口方法（带 merchantId）
    @DbRateLimit(layer = DbRateLimitLayer.CALC)
    public void executeTask(String billNo, Long merchantId) {
        long consumeStart = clearanceTaskMetrics.nanoTime(); // 整单消费起点
        boolean success = false; // 是否清算成功
        try { // 主流程
            Long resolvedMerchantId = resolveMerchantId(billNo, merchantId); // 解析分片键

            if (shouldSkipTerminalTask(billNo, resolvedMerchantId)) { // PR-C2：终态任务快速跳过
                clearanceTaskMetrics.recordSkip(); // 跳过计数
                return; // ACK，避免 claim 空转
            }

            long claimStart = clearanceTaskMetrics.nanoTime(); // 阶段 1 起点
            Optional<ClearanceClaimContext> claimOpt =
                    clearanceTaskTxSupport.claimAndMarkClearing(billNo, resolvedMerchantId); // 抢占+标 CLEARING
            clearanceTaskMetrics.recordStage(ClearanceTaskMetrics.STAGE_CLAIM, claimStart); // 记录 claim 耗时
            if (claimOpt.isEmpty()) { // 未抢到或已处理
                handleUnclaimed(billNo, resolvedMerchantId); // 幂等/DEAD 短路
                clearanceTaskMetrics.recordSkip(); // 跳过计数
                return; // ACK 结束
            }
            ClearanceClaimContext claimCtx = claimOpt.get(); // claim 上下文
            TradeBillEntity bill = claimCtx.bill(); // 取出账单

            // 主数据走 Redis 缓存，放在短事务之外，减少持连期间的 config 往返
            AgentRelationDTO relation = merchantValidateService.loadRelation(bill.merchantId); // 加载代理关系

            try { // 阶段 2+3
                long coreStart = clearanceTaskMetrics.nanoTime(); // 阶段 2 起点
                clearanceTaskTxSupport.runFeeAndSplit(bill, relation); // PR-E：计费+分账合并短事务
                clearanceTaskMetrics.recordStage(ClearanceTaskMetrics.STAGE_FEE_SPLIT, coreStart); // 记录 fee_split 耗时

                long finStart = clearanceTaskMetrics.nanoTime(); // 阶段 3 起点
                clearanceTaskTxSupport.finalizeSuccess(claimCtx); // 标 CLEARED+SUCCESS
                clearanceTaskMetrics.recordStage(ClearanceTaskMetrics.STAGE_FINALIZE, finStart); // 记录 finalize 耗时

                businessMetrics.recordThroughput(PayBusinessMetrics.STAGE_CLEARANCE_DONE, true); // 业务吞吐指标
                activateWaitingRefunds(billNo); // 激活等待原单的退款
                success = true; // 标记成功
            } catch (NonRetryableException e) { // 不可重试
                throw e; // 原样抛出给 MQ Invoker
            } catch (Exception e) { // 可重试业务失败
                log.error("clearance failed billNo={}", billNo, e); // 错误日志
                long failStart = clearanceTaskMetrics.nanoTime(); // 失败处理起点
                ClearanceFailureOutcome failure = clearanceTaskTxSupport.markFailure(claimCtx, e);
                notifyIfEnteredDead(failure);
                clearanceTaskMetrics.recordStage(ClearanceTaskMetrics.STAGE_FAIL, failStart); // 记录 fail 耗时
            }
        } finally { // 无论成败记录整单耗时
            clearanceTaskMetrics.recordConsume(consumeStart, success); // 写入 consume_duration
        }
    }

    /**
     * 解析 merchantId：MQ 载荷 → clearance_task → bill_route。
     * 接入同事务内 bill_route 可能尚未对 config 数据源可见，故优先用任务表。
     */
    private Long resolveMerchantId(String billNo, Long merchantIdHint) {
        if (merchantIdHint != null) {
            return merchantIdHint;
        }
        Optional<ClearanceTaskEntity> task = clearanceTaskRepository.findByBillNo(billNo);
        if (task.isPresent() && task.get().merchantId != null) {
            return task.get().merchantId;
        }
        return shardRouteService.requireMerchantIdByBillNo(billNo);
    }

    /**
     * PR-C2：终态任务（SUCCESS/DEAD）在 claim 前快速跳过，减少脏 MQ 空转。
     */
    private boolean shouldSkipTerminalTask(String billNo, Long merchantId) {
        Optional<Integer> statusOpt = clearanceTaskRepository.findStatusByBillNoAndMerchantId(billNo, merchantId);
        if (statusOpt.isEmpty()) {
            return false;
        }
        int status = statusOpt.get();
        if (status == TaskStatus.SUCCESS.getCode()) {
            return true;
        }
        if (status == TaskStatus.DEAD.getCode()) {
            log.debug("clearance fast-skip dead task billNo={}", billNo);
            return true;
        }
        return false;
    }

    /**
     * 抢占失败时的幂等与 DEAD 处理（避免 MQ L1 与 L2 双重重试浪费）。
     */
    private void handleUnclaimed(String billNo, Long merchantId) {
        Optional<Integer> statusOpt = clearanceTaskRepository.findStatusByBillNoAndMerchantId(billNo, merchantId);
        if (statusOpt.isEmpty()) {
            return;
        }
        int status = statusOpt.get();
        if (status == TaskStatus.SUCCESS.getCode()) {
            return;
        }
        if (status == TaskStatus.DEAD.getCode()) {
            log.warn("clearance skip dead task billNo={}", billNo);
            return;
        }
        if (status == TaskStatus.RUNNING.getCode()) {
            return;
        }
    }

    @Override
    public void watchdogFailAndNotify(String billNo, Long merchantId) {
        ClearanceFailureOutcome outcome = clearanceTaskTxSupport.watchdogFail(billNo, merchantId);
        notifyIfEnteredDead(outcome);
    }

    private void notifyIfEnteredDead(ClearanceFailureOutcome outcome) {
        if (outcome == null || !outcome.enteredDead()) {
            return;
        }
        exceptionRecordService.openClearanceDead(outcome.billNo(), outcome.errorMsg());
        alertService.send(AlertService.CLEARANCE_DEAD,
                "billNo=" + outcome.billNo() + " err=" + outcome.errorMsg());
    }

    /**
     * 重试失败任务，限制单次处理数量（由 ClearanceRetryJob 调用）。
     */
    @Override // 实现接口方法
    public void retryFailedTasks(int limit) {
        int perShard = ShardScanSupport.perShardLimit(limit);
        List<ClearanceTaskEntity> tasks = ShardScanSupport.collectAcrossShards(shardId ->
                clearanceTaskRepository.findFailedReadyForRetryByShard(
                        TaskStatus.FAILED.getCode(), MAX_RETRY, LocalDateTime.now(), shardId, perShard));
        tasks.stream().limit(limit).forEach(t -> {
            if (!clearanceTaskRepository.resetToPending(
                    t.billNo, t.merchantId, TaskStatus.FAILED.getCode(),
                    TaskStatus.PENDING.getCode(), MAX_RETRY, LocalDateTime.now())) {
                return;
            }
            if (payMqProperties.isClearanceViaMq()) {
                clearanceTaskPublisher.publish(t.billNo, t.merchantId);
            } else {
                executeTask(t.billNo, t.merchantId);
            }
        });
    }

    /**
     * 原单清算成功后，激活等待原单的退款单（改发 MQ，不阻塞消费线程）。
     */
    private void activateWaitingRefunds(String clearedBillNo) {
        List<TradeBillEntity> waiting = tradeBillRepository.findByStatusAndOriginBillNo( // 查询等待原单的退款
                BillStatus.WAIT_ORIGIN.getCode(), clearedBillNo);
        for (TradeBillEntity refund : waiting) {
            int updated = tradeBillRepository.updateStatusByBillNoAndMerchantId(
                    refund.billNo, refund.merchantId,
                    BillStatus.WAIT_ORIGIN.getCode(), BillStatus.PENDING.getCode());
            if (updated == 0) {
                continue;
            }
            createTask(refund.billNo, refund.merchantId);
            if (payMqProperties.isClearanceViaMq()) {
                clearanceTaskPublisher.publish(refund.billNo, refund.merchantId);
            } else {
                executeTask(refund.billNo, refund.merchantId);
            }
        }
    }
}
