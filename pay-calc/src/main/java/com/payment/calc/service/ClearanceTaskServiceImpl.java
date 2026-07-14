package com.payment.calc.service; // 清算计算服务包

import com.payment.api.dto.AgentRelationDTO; // 代理关系 DTO
import com.payment.api.dto.FeeCalcDTO; // 费用计算请求 DTO
import com.payment.api.dto.FeeCalcResultDTO; // 费用计算结果 DTO
import com.payment.api.service.ClearanceTaskService; // 清算任务服务接口
import com.payment.api.service.FeeCalcService; // 费用计算服务接口
import com.payment.api.service.MerchantValidateService; // 商户校验服务接口
import com.payment.api.service.SplitService; // 分账服务接口
import com.payment.calc.support.ClearanceTaskPublisher; // 清算 MQ 发布器
import com.payment.common.enums.BillStatus; // 账单状态枚举
import com.payment.common.enums.BillType; // 账单类型枚举
import com.payment.common.enums.TaskStatus; // 任务状态枚举
import com.payment.common.shard.ShardRouter;
import com.payment.common.metrics.PayBusinessMetrics; // 业务吞吐指标
import com.payment.control.service.AlertService; // 告警服务
import com.payment.control.service.ExceptionRecordService; // 异常工单服务
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
import java.time.Duration;
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
    private final FeeCalcService feeCalcService; // 费用计算服务
    private final SplitService splitService; // 分账服务
    private final MerchantValidateService merchantValidateService; // 商户校验服务
    private final PayMqProperties payMqProperties; // MQ 模式开关
    private final ClearanceTaskPublisher clearanceTaskPublisher; // 清算 MQ 发布
    private final ExceptionRecordService exceptionRecordService; // 异常工单
    private final AlertService alertService; // 告警
    private final PayBusinessMetrics businessMetrics; // 业务吞吐指标

    /**
     * 构造注入依赖。
     */
    public ClearanceTaskServiceImpl(ClearanceTaskRepository clearanceTaskRepository,
                                    TradeBillRepository tradeBillRepository,
                                    ShardRouteService shardRouteService,
                                    FeeCalcService feeCalcService,
                                    SplitService splitService,
                                    MerchantValidateService merchantValidateService,
                                    PayMqProperties payMqProperties,
                                    ClearanceTaskPublisher clearanceTaskPublisher,
                                    ExceptionRecordService exceptionRecordService,
                                    AlertService alertService,
                                    PayBusinessMetrics businessMetrics) {
        this.clearanceTaskRepository = clearanceTaskRepository; // 赋值任务仓储
        this.tradeBillRepository = tradeBillRepository; // 赋值账单仓储
        this.shardRouteService = shardRouteService; // 赋值路由服务
        this.feeCalcService = feeCalcService; // 赋值费用服务
        this.splitService = splitService; // 赋值分账服务
        this.merchantValidateService = merchantValidateService; // 赋值校验服务
        this.payMqProperties = payMqProperties; // 赋值 MQ 配置
        this.clearanceTaskPublisher = clearanceTaskPublisher; // 赋值发布器
        this.exceptionRecordService = exceptionRecordService; // 赋值工单服务
        this.alertService = alertService; // 赋值告警服务
        this.businessMetrics = businessMetrics; // 赋值指标
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
    @DSTransactional
    public void executeTask(String billNo) {
        executeTask(billNo, null);
    }

    @Override
    @DSTransactional
    public void executeTask(String billNo, Long merchantId) {
        Long resolvedMerchantId = resolveMerchantId(billNo, merchantId);
        int claimed = clearanceTaskRepository.claimTask( // 抢占 PENDING → RUNNING
                billNo, resolvedMerchantId, TaskStatus.PENDING.getCode(), TaskStatus.RUNNING.getCode(), LocalDateTime.now());
        if (claimed == 0) { // 抢占失败
            handleUnclaimed(billNo); // 幂等/DEAD 短路
            return; // 结束
        }

        TradeBillEntity bill = tradeBillRepository.findByBillNo(billNo).orElseThrow(); // 查询账单
        bill.status = BillStatus.CLEARING.getCode(); // 更新为清算中
        tradeBillRepository.save(bill); // 保存账单

        try { // 执行清算逻辑
            AgentRelationDTO relation = merchantValidateService.loadRelation(bill.merchantId); // 加载代理关系
            FeeCalcResultDTO result; // 费用计算结果
            if (bill.billType == BillType.REFUND.getCode()) { // 退款单
                result = feeCalcService.calcRefundFee(bill.originBillNo, billNo, bill.tradeAmount); // 计算退款费用
            } else { // 正向交易
                FeeCalcDTO req = new FeeCalcDTO(); // 构建费用计算请求
                req.billNo = billNo; // 账单号
                req.merchantId = bill.merchantId; // 商户 ID
                req.agentId = relation.agentId != null ? relation.agentId : bill.agentId; // 一级代理 ID
                req.secondAgentId = relation.secondAgentId != null ? relation.secondAgentId : bill.secondAgentId; // 二级代理
                req.splitPartyId = relation.splitPartyId; // 合作方 ID
                req.tradeAmount = bill.tradeAmount; // 交易金额
                req.businessLine = bill.businessLine; // 业务线
                req.category = bill.category; // 品类
                req.serviceItem = bill.serviceItem; // 服务项目
                req.cityCode = bill.cityCode; // 城市编码
                result = feeCalcService.calcShareFee(req); // 计算正向分润
            }

            splitService.generateSplitDetail(result, relation); // 生成分账明细与 Outbox

            bill.status = BillStatus.CLEARED.getCode(); // 更新为已清算
            tradeBillRepository.save(bill); // 保存账单

            ClearanceTaskEntity task = clearanceTaskRepository.findByBillNo(billNo).orElseThrow(); // 查询任务
            task.status = TaskStatus.SUCCESS.getCode(); // 更新为成功
            task.errorMsg = null; // 清空错误信息
            task.nextRetryTime = null; // 清空下次重试
            clearanceTaskRepository.save(task); // 保存任务
            businessMetrics.recordThroughput(PayBusinessMetrics.STAGE_CLEARANCE_DONE, true);

            activateWaitingRefunds(billNo); // 异步激活等待原单的退款
        } catch (Exception e) { // 清算失败
            log.error("clearance failed billNo={}", billNo, e); // 记录错误日志
            ClearanceTaskEntity task = clearanceTaskRepository.findByBillNo(billNo).orElseThrow(); // 查询任务
            task.status = TaskStatus.FAILED.getCode(); // 更新为失败
            task.retryCount = task.retryCount + 1; // 重试次数加一
            task.errorMsg = e.getMessage(); // 记录错误信息
            task.nextRetryTime = LocalDateTime.now().plus(backoffDuration(task.retryCount)); // 指数退避
            if (task.retryCount >= MAX_RETRY) { // 超过最大重试
                task.status = TaskStatus.DEAD.getCode(); // 标记为死信
                exceptionRecordService.openClearanceDead(billNo, task.errorMsg); // 建 EX-0201 工单
                alertService.send(AlertService.CLEARANCE_DEAD, // P2 告警
                        "billNo=" + billNo + " err=" + task.errorMsg); // 告警内容
            }
            clearanceTaskRepository.save(task); // 保存任务

            bill.status = BillStatus.FAILED.getCode(); // 账单标记失败
            tradeBillRepository.save(bill); // 保存账单
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
            throw new NonRetryableException("clearance task dead billNo=" + billNo); // 通知 Listener 进 DLQ
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

    /** 业务重试退避：1/5/15/30/60 分钟 */
    private Duration backoffDuration(int retryCount) {
        return switch (retryCount) { // 按 retry_count 阶梯
            case 1 -> Duration.ofMinutes(1); // 第 1 次失败 +1min
            case 2 -> Duration.ofMinutes(5); // 第 2 次 +5min
            case 3 -> Duration.ofMinutes(15); // 第 3 次 +15min
            case 4 -> Duration.ofMinutes(30); // 第 4 次 +30min
            default -> Duration.ofMinutes(60); // 第 5 次 +60min
        };
    }
}
