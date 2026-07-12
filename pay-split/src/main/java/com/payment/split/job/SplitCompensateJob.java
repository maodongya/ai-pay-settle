package com.payment.split.job; // 分账定时任务包

import com.payment.api.dto.FeeCalcResultDTO; // 费用计算结果 DTO
import com.payment.domain.entity.ClearanceTaskEntity; // 清算任务实体
import com.payment.domain.entity.FeeCalcResultEntity; // 费用计算结果实体
import com.payment.domain.repository.ClearanceTaskRepository; // 清算任务仓储
import com.payment.domain.repository.FeeCalcResultRepository; // 费用计算结果仓储
import com.payment.domain.repository.SplitDetailRepository; // 分账明细仓储
import com.payment.split.support.SplitCompensateSupport; // 分账补偿支持
import com.payment.common.enums.TaskStatus; // 任务状态枚举
import org.slf4j.Logger; // 日志接口
import org.slf4j.LoggerFactory; // 日志工厂
import org.springframework.scheduling.annotation.Scheduled; // 定时任务注解
import org.springframework.stereotype.Component; // Spring 组件注解

import java.util.List; // 列表

/**
 * 分账补偿定时任务，为失败但已有计费结果的任务补录分账。
 */
@Component // 注册为 Spring 组件
public class SplitCompensateJob {

    private static final Logger log = LoggerFactory.getLogger(SplitCompensateJob.class); // 日志记录器

    private final ClearanceTaskRepository clearanceTaskRepository; // 清算任务仓储
    private final FeeCalcResultRepository feeCalcResultRepository; // 费用计算结果仓储
    private final SplitDetailRepository splitDetailRepository; // 分账明细仓储
    private final SplitCompensateSupport splitCompensateSupport; // 分账补偿支持

    /**
     * 构造注入依赖。
     */
    public SplitCompensateJob(ClearanceTaskRepository clearanceTaskRepository,
                              FeeCalcResultRepository feeCalcResultRepository,
                              SplitDetailRepository splitDetailRepository,
                              SplitCompensateSupport splitCompensateSupport) {
        this.clearanceTaskRepository = clearanceTaskRepository; // 赋值任务仓储
        this.feeCalcResultRepository = feeCalcResultRepository; // 赋值计费结果仓储
        this.splitDetailRepository = splitDetailRepository; // 赋值分账仓储
        this.splitCompensateSupport = splitCompensateSupport; // 赋值补偿支持
    }

    /**
     * 定时扫描失败任务并补偿分账。
     */
    @Scheduled(fixedDelayString = "${pay.compensate.split-interval-ms:600000}") // 默认每 10 分钟执行
    public void compensate() {
        List<ClearanceTaskEntity> failed = clearanceTaskRepository.findByStatusOrderByCreateTimeAsc(TaskStatus.FAILED.getCode()); // 查询失败任务
        for (ClearanceTaskEntity task : failed) { // 逐个处理
            if (splitDetailRepository.existsByBillNo(task.billNo)) { // 分账已存在
                continue; // 跳过
            }
            feeCalcResultRepository.findByBillNo(task.billNo).ifPresent(result -> { // 有计费结果时
                try { // 执行补偿
                    splitCompensateSupport.compensate(toDto(result)); // 补偿分账
                    task.status = TaskStatus.SUCCESS.getCode(); // 更新为成功
                    task.errorMsg = null; // 清空错误信息
                    clearanceTaskRepository.save(task); // 保存任务
                    log.info("split compensated billNo={}", task.billNo); // 记录成功日志
                } catch (Exception e) { // 补偿失败
                    log.warn("split compensate failed billNo={}", task.billNo, e); // 记录警告日志
                }
            });
        }
    }

    /**
     * 将计费结果实体转换为 DTO。
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
