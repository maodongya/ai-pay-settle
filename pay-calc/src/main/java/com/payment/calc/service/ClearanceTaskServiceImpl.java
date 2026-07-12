package com.payment.calc.service; // 清算计算服务包

import com.payment.api.dto.AgentRelationDTO; // 代理关系 DTO
import com.payment.api.dto.FeeCalcDTO; // 费用计算请求 DTO
import com.payment.api.dto.FeeCalcResultDTO; // 费用计算结果 DTO
import com.payment.api.service.ClearanceTaskService; // 清算任务服务接口
import com.payment.api.service.FeeCalcService; // 费用计算服务接口
import com.payment.api.service.MerchantValidateService; // 商户校验服务接口
import com.payment.api.service.SplitService; // 分账服务接口
import com.payment.common.enums.BillStatus; // 账单状态枚举
import com.payment.common.enums.BillType; // 账单类型枚举
import com.payment.common.enums.TaskStatus; // 任务状态枚举
import com.payment.domain.entity.ClearanceTaskEntity; // 清算任务实体
import com.payment.domain.entity.TradeBillEntity; // 交易账单实体
import com.payment.domain.repository.ClearanceTaskRepository; // 清算任务仓储
import com.payment.domain.repository.TradeBillRepository; // 交易账单仓储
import org.slf4j.Logger; // 日志接口
import org.slf4j.LoggerFactory; // 日志工厂
import org.springframework.stereotype.Service; // Spring 服务注解
import org.springframework.transaction.annotation.Transactional; // 事务注解

import java.time.LocalDateTime; // 本地日期时间
import java.util.List; // 列表

/**
 * 清算任务服务实现，负责创建、执行和重试清算任务。
 */
@Service // 注册为 Spring 服务
public class ClearanceTaskServiceImpl implements ClearanceTaskService {

    private static final Logger log = LoggerFactory.getLogger(ClearanceTaskServiceImpl.class); // 日志记录器
    private static final int MAX_RETRY = 5; // 最大重试次数

    private final ClearanceTaskRepository clearanceTaskRepository; // 清算任务仓储
    private final TradeBillRepository tradeBillRepository; // 交易账单仓储
    private final FeeCalcService feeCalcService; // 费用计算服务
    private final SplitService splitService; // 分账服务
    private final MerchantValidateService merchantValidateService; // 商户校验服务

    /**
     * 构造注入依赖。
     */
    public ClearanceTaskServiceImpl(ClearanceTaskRepository clearanceTaskRepository,
                                    TradeBillRepository tradeBillRepository,
                                    FeeCalcService feeCalcService,
                                    SplitService splitService,
                                    MerchantValidateService merchantValidateService) {
        this.clearanceTaskRepository = clearanceTaskRepository; // 赋值任务仓储
        this.tradeBillRepository = tradeBillRepository; // 赋值账单仓储
        this.feeCalcService = feeCalcService; // 赋值费用服务
        this.splitService = splitService; // 赋值分账服务
        this.merchantValidateService = merchantValidateService; // 赋值校验服务
    }

    /**
     * 创建清算任务，已存在则跳过。
     */
    @Override // 实现接口方法
    @Transactional // 开启事务
    public void createTask(String billNo, Long merchantId) {
        if (clearanceTaskRepository.findByBillNo(billNo).isPresent()) { // 任务已存在
            return; // 直接返回
        }
        ClearanceTaskEntity task = new ClearanceTaskEntity(); // 创建任务实体
        task.billNo = billNo; // 账单号
        task.merchantId = merchantId; // 商户 ID
        task.shardId = (int) (merchantId % 16); // 分片 ID
        task.status = TaskStatus.PENDING.getCode(); // 待处理状态
        task.retryCount = 0; // 重试次数归零
        task.createTime = LocalDateTime.now(); // 创建时间
        task.updateTime = LocalDateTime.now(); // 更新时间
        clearanceTaskRepository.save(task); // 保存任务
    }

    /**
     * 执行清算任务：计费→分账→更新状态。
     */
    @Override // 实现接口方法
    @Transactional // 开启事务
    public void executeTask(String billNo) {
        int claimed = clearanceTaskRepository.claimTask( // 抢占任务
                billNo, TaskStatus.PENDING.getCode(), TaskStatus.RUNNING.getCode(), LocalDateTime.now());
        if (claimed == 0) { // 抢占失败
            return; // 直接返回
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
                req.secondAgentId = relation.secondAgentId != null ? relation.secondAgentId : bill.secondAgentId; // 二级代理 ID
                req.splitPartyId = relation.splitPartyId; // 合作方 ID
                req.tradeAmount = bill.tradeAmount; // 交易金额
                req.businessLine = bill.businessLine; // 业务线
                req.category = bill.category; // 品类
                req.serviceItem = bill.serviceItem; // 服务项目
                req.cityCode = bill.cityCode; // 城市编码
                result = feeCalcService.calcShareFee(req); // 计算正向分润
            }

            splitService.generateSplitDetail(result, relation); // 生成分账明细

            bill.status = BillStatus.CLEARED.getCode(); // 更新为已清算
            tradeBillRepository.save(bill); // 保存账单

            ClearanceTaskEntity task = clearanceTaskRepository.findByBillNo(billNo).orElseThrow(); // 查询任务
            task.status = TaskStatus.SUCCESS.getCode(); // 更新为成功
            task.errorMsg = null; // 清空错误信息
            clearanceTaskRepository.save(task); // 保存任务

            activateWaitingRefunds(billNo); // 激活等待原单的退款单
        } catch (Exception e) { // 清算失败
            log.error("clearance failed billNo={}", billNo, e); // 记录错误日志
            ClearanceTaskEntity task = clearanceTaskRepository.findByBillNo(billNo).orElseThrow(); // 查询任务
            task.status = TaskStatus.FAILED.getCode(); // 更新为失败
            task.retryCount = task.retryCount + 1; // 重试次数加一
            task.errorMsg = e.getMessage(); // 记录错误信息
            if (task.retryCount >= MAX_RETRY) { // 超过最大重试
                task.status = TaskStatus.DEAD.getCode(); // 标记为死信
            }
            clearanceTaskRepository.save(task); // 保存任务

            bill.status = BillStatus.FAILED.getCode(); // 账单标记失败
            tradeBillRepository.save(bill); // 保存账单
        }
    }

    /**
     * 重试失败任务，限制单次处理数量。
     */
    @Override // 实现接口方法
    public void retryFailedTasks(int limit) {
        List<ClearanceTaskEntity> tasks = clearanceTaskRepository.findByStatusAndRetryCountLessThan( // 查询可重试任务
                TaskStatus.FAILED.getCode(), MAX_RETRY);
        tasks.stream().limit(limit).forEach(t -> { // 限制处理数量
            t.status = TaskStatus.PENDING.getCode(); // 重置为待处理
            clearanceTaskRepository.save(t); // 保存任务
            executeTask(t.billNo); // 立即执行
        });
    }

    /**
     * 原单清算成功后，激活等待原单的退款单。
     */
    private void activateWaitingRefunds(String clearedBillNo) {
        List<TradeBillEntity> waiting = tradeBillRepository.findByStatusAndOriginBillNo( // 查询等待原单的退款
                BillStatus.WAIT_ORIGIN.getCode(), clearedBillNo);
        for (TradeBillEntity refund : waiting) { // 逐个激活
            refund.status = BillStatus.PENDING.getCode(); // 更新为待处理
            tradeBillRepository.save(refund); // 保存退款单
            createTask(refund.billNo, refund.merchantId); // 创建清算任务
            executeTask(refund.billNo); // 执行清算
        }
    }
}
