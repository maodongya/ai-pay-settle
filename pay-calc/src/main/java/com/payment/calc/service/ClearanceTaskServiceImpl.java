package com.payment.calc.service; // 清算计算服务包

import com.payment.api.dto.AgentRelationDTO; // 代理关系 DTO
import com.payment.api.service.ClearanceTaskService; // 清算任务服务接口
import com.payment.api.service.MerchantValidateService; // 商户校验服务接口
import com.payment.calc.metrics.ClearanceTaskMetrics; // calc 清算监控指标
import com.payment.calc.support.ClearanceTaskPublisher; // 清算 MQ 发布器
import com.payment.common.enums.BillStatus; // 账单状态枚举
import com.payment.common.enums.TaskStatus; // 任务状态枚举
import com.payment.common.shard.ShardRouter;
import com.payment.common.metrics.PayBusinessMetrics; // 业务吞吐指标
import com.payment.domain.entity.ClearanceTaskEntity; // 清算任务实体
import com.payment.domain.entity.TradeBillEntity; // 交易账单实体
import com.payment.domain.repository.ClearanceTaskRepository; // 清算任务仓储
import com.payment.domain.repository.TradeBillRepository; // 交易账单仓储
import com.payment.domain.service.ShardRouteService; // 分片路由
import com.payment.mq.config.PayMqProperties; // MQ 开关
import com.payment.mq.exception.NonRetryableException; // 不可 MQ 重试异常
import org.slf4j.Logger; // 日志接口
import org.slf4j.LoggerFactory; // 日志工厂
import com.baomidou.dynamic.datasource.annotation.DSTransactional;
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
                                    ClearanceTaskMetrics clearanceTaskMetrics) {
        this.clearanceTaskRepository = clearanceTaskRepository; // 赋值任务仓储
        this.tradeBillRepository = tradeBillRepository; // 赋值账单仓储
        this.shardRouteService = shardRouteService; // 赋值路由服务
        this.merchantValidateService = merchantValidateService; // 赋值校验服务
        this.payMqProperties = payMqProperties; // 赋值 MQ 配置
        this.clearanceTaskPublisher = clearanceTaskPublisher; // 赋值发布器
        this.businessMetrics = businessMetrics; // 赋值指标
        this.clearanceTaskTxSupport = clearanceTaskTxSupport; // 赋值分阶段事务
        this.clearanceTaskMetrics = clearanceTaskMetrics; // 赋值 calc 指标
    }

    /**
     * 创建清算任务，已存在则跳过。
     */
    @Override // 实现接口方法
    @DSTransactional // 多数据源
    public void createTask(String billNo, Long merchantId) {
        if (clearanceTaskRepository.findByBillNo(billNo).isPresent()) { // 任务已存在
            return; // 直接返回（幂等）
        }
        ClearanceTaskEntity task = new ClearanceTaskEntity(); // 创建任务实体
        task.billNo = billNo; // 账单号
        task.merchantId = merchantId; // 商户 ID
        task.shardId = ShardRouter.shardId(merchantId); // 分片 ID，与 Queue 数 16 对齐
        task.status = TaskStatus.PENDING.getCode(); // 待处理状态
        task.retryCount = 0; // 重试次数归零
        task.createTime = LocalDateTime.now(); // 创建时间
        task.updateTime = LocalDateTime.now(); // 更新时间
        clearanceTaskRepository.save(task); // 保存任务
    }

    /**
     * 执行清算任务：计费→分账→更新状态；协调 MQ L1 与业务 L2 重试。
     */
    @Override // 实现接口方法
    public void executeTask(String billNo) {
        executeTask(billNo, null); // 委托带 merchantId 的重载
    }

    @Override // 实现接口方法（带 merchantId）
    public void executeTask(String billNo, Long merchantId) {
        long consumeStart = clearanceTaskMetrics.nanoTime(); // 整单消费起点
        boolean success = false; // 是否清算成功
        try { // 主流程
            Long resolvedMerchantId = resolveMerchantId(billNo, merchantId); // 解析分片键

            long claimStart = clearanceTaskMetrics.nanoTime(); // 阶段 1 起点
            Optional<TradeBillEntity> billOpt = clearanceTaskTxSupport.claimAndMarkClearing(billNo, resolvedMerchantId); // 抢占+标 CLEARING
            clearanceTaskMetrics.recordStage(ClearanceTaskMetrics.STAGE_CLAIM, claimStart); // 记录 claim 耗时
            if (billOpt.isEmpty()) { // 未抢到或已处理
                handleUnclaimed(billNo); // 幂等/DEAD 短路
                clearanceTaskMetrics.recordSkip(); // 跳过计数
                return; // ACK 结束
            }
            TradeBillEntity bill = billOpt.get(); // 取出账单

            // 主数据走 Redis 缓存，放在短事务之外，减少持连期间的 config 往返
            AgentRelationDTO relation = merchantValidateService.loadRelation(bill.merchantId); // 加载代理关系

            try { // 阶段 2+3
                long coreStart = clearanceTaskMetrics.nanoTime(); // 阶段 2 起点
                clearanceTaskTxSupport.runFeeAndSplit(bill, relation); // 计费+分账
                clearanceTaskMetrics.recordStage(ClearanceTaskMetrics.STAGE_FEE_SPLIT, coreStart); // 记录 fee_split 耗时

                long finStart = clearanceTaskMetrics.nanoTime(); // 阶段 3 起点
                clearanceTaskTxSupport.finalizeSuccess(bill); // 标 CLEARED+SUCCESS
                clearanceTaskMetrics.recordStage(ClearanceTaskMetrics.STAGE_FINALIZE, finStart); // 记录 finalize 耗时

                businessMetrics.recordThroughput(PayBusinessMetrics.STAGE_CLEARANCE_DONE, true); // 业务吞吐指标
                activateWaitingRefunds(billNo); // 激活等待原单的退款
                success = true; // 标记成功
            } catch (NonRetryableException e) { // 不可重试
                throw e; // 原样抛出给 MQ Invoker
            } catch (Exception e) { // 可重试业务失败
                log.error("clearance failed billNo={}", billNo, e); // 错误日志
                long failStart = clearanceTaskMetrics.nanoTime(); // 失败处理起点
                clearanceTaskTxSupport.markFailure(bill, e); // 标 FAILED/DEAD
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
     * 抢占失败时的幂等与 DEAD 处理（避免 MQ L1 与 L2 双重重试浪费）。
     */
    private void handleUnclaimed(String billNo) {
        Optional<ClearanceTaskEntity> opt = clearanceTaskRepository.findByBillNo(billNo); // 查询任务
        if (opt.isEmpty()) { // 任务不存在
            return; // 忽略
        }
        ClearanceTaskEntity task = opt.get(); // 取出任务
        if (task.status == TaskStatus.SUCCESS.getCode()) { // 已成功
            return; // 幂等 ACK
        }
        if (task.status == TaskStatus.DEAD.getCode()) { // 已 DEAD
            log.warn("clearance skip dead task billNo={}", billNo);
            return; // 直接 ACK，避免 RETRY/DLQ 反复消费
        }
        if (task.status == TaskStatus.RUNNING.getCode()) { // 其他线程执行中
            return; // 不重复执行
        }
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
        tasks.stream().limit(limit).forEach(t -> { // 限制批量
            t.status = TaskStatus.PENDING.getCode(); // 重置为待处理
            clearanceTaskRepository.save(t); // 保存任务
            if (payMqProperties.isClearanceViaMq()) { // MQ 模式
                clearanceTaskPublisher.publish(t.billNo, t.merchantId); // 发 MQ 而非 sync execute
            } else { // 同步模式
                executeTask(t.billNo, t.merchantId); // 本地直接执行
            }
        });
    }

    /**
     * 原单清算成功后，激活等待原单的退款单（改发 MQ，不阻塞消费线程）。
     */
    private void activateWaitingRefunds(String clearedBillNo) {
        List<TradeBillEntity> waiting = tradeBillRepository.findByStatusAndOriginBillNo( // 查询等待原单的退款
                BillStatus.WAIT_ORIGIN.getCode(), clearedBillNo);
        for (TradeBillEntity refund : waiting) { // 逐个激活
            refund.status = BillStatus.PENDING.getCode(); // 更新为待处理
            tradeBillRepository.save(refund); // 保存退款单
            createTask(refund.billNo, refund.merchantId); // 创建清算任务
            if (payMqProperties.isClearanceViaMq()) { // MQ 模式
                clearanceTaskPublisher.publish(refund.billNo, refund.merchantId); // 异步清算
            } else { // 同步模式
                executeTask(refund.billNo, refund.merchantId); // 本地执行
            }
        }
    }
}
