package com.payment.access.service; // 账单接入服务包

import com.payment.common.metrics.PayBusinessMetrics;
import com.payment.api.dto.TradeBillDTO; // 账单 DTO
import com.payment.api.dto.ValidateResult; // 校验结果
import com.payment.api.service.BillAccessService; // 接入服务接口
import com.payment.api.service.ClearanceTaskService; // 清算任务服务
import com.payment.api.service.MerchantValidateService; // 商户校验
import com.payment.calc.support.ClearanceTaskPublisher; // 清算 MQ 发布（同模块经 pay-calc 传递）
import com.payment.common.enums.BillStatus; // 账单状态
import com.payment.common.enums.BillType; // 账单类型
import com.payment.common.exception.BizException; // 业务异常
import com.payment.domain.entity.TradeBillEntity; // 账单实体
import com.payment.domain.repository.TradeBillRepository; // 账单仓储
import com.payment.domain.service.ShardRouteService;
import com.payment.mq.config.PayMqProperties; // MQ 配置
import com.payment.mq.support.MqBacklogState; // 积压熔断状态
import org.springframework.stereotype.Service; // 服务注解
import org.springframework.transaction.annotation.Transactional; // 事务

import java.time.LocalDateTime; // 时间

/**
 * 账单接入服务实现，接收交易账单并触发清算流程。
 */
@Service // 注册为 Spring 服务
public class BillAccessServiceImpl implements BillAccessService {

    private final TradeBillRepository tradeBillRepository; // 账单仓储
    private final MerchantValidateService merchantValidateService; // 商户校验
    private final ClearanceTaskService clearanceTaskService; // 清算任务
    private final PayMqProperties payMqProperties; // MQ 开关
    private final ClearanceTaskPublisher clearanceTaskPublisher; // 有序发 clearance_task
    private final MqBacklogState mqBacklogState; // 积压熔断
    private final PayBusinessMetrics businessMetrics; // 业务吞吐指标
    private final ShardRouteService shardRouteService; // 分片路由

    /** 构造注入 */
    public BillAccessServiceImpl(TradeBillRepository tradeBillRepository,
                                 MerchantValidateService merchantValidateService,
                                 ClearanceTaskService clearanceTaskService,
                                 PayMqProperties payMqProperties,
                                 ClearanceTaskPublisher clearanceTaskPublisher,
                                 MqBacklogState mqBacklogState,
                                 PayBusinessMetrics businessMetrics,
                                 ShardRouteService shardRouteService) {
        this.tradeBillRepository = tradeBillRepository; // 账单仓储
        this.merchantValidateService = merchantValidateService; // 校验服务
        this.clearanceTaskService = clearanceTaskService; // 清算服务
        this.payMqProperties = payMqProperties; // MQ 配置
        this.clearanceTaskPublisher = clearanceTaskPublisher; // 发布器
        this.mqBacklogState = mqBacklogState; // 熔断状态
        this.businessMetrics = businessMetrics; // 指标
        this.shardRouteService = shardRouteService; // 分片路由
    }

    /**
     * 提交交易账单，已存在则幂等返回。
     */
    @Override // 实现接口
    @Transactional // 事务
    public TradeBillDTO submitBill(TradeBillDTO bill) {
        if (mqBacklogState.isCircuitOpen() && payMqProperties.isEnabled()) { // 积压熔断打开
            throw new BizException(503, "service overloaded, retry later: " + mqBacklogState.getLastSummary()); // HTTP 503 语义
        }
        return tradeBillRepository.findByBillNo(bill.billNo) // 幂等查询
                .map(this::toDto) // 已存在
                .orElseGet(() -> createBill(bill)); // 新建
    }

    /** 创建账单并触发清算 */
    private TradeBillDTO createBill(TradeBillDTO bill) {
        ValidateResult validation = merchantValidateService.validateBill(bill); // 校验
        if (!validation.valid) { // 失败
            throw new BizException(validation.errorCode, validation.message); // 业务异常
        }

        int status = BillStatus.PENDING.getCode(); // 默认待处理
        if (bill.billType == BillType.REFUND.getCode()) { // 退款
            TradeBillEntity origin = tradeBillRepository.findByBillNo(bill.originBillNo).orElseThrow(); // 原单
            if (origin.status != BillStatus.CLEARED.getCode()) { // 原单未清算
                status = BillStatus.WAIT_ORIGIN.getCode(); // 等待原单
            }
        }

        TradeBillEntity entity = new TradeBillEntity(); // 实体
        entity.billNo = bill.billNo; // 账单号
        entity.billType = bill.billType; // 类型
        entity.businessLine = bill.businessLine; // 业务线
        entity.category = bill.category; // 品类
        entity.serviceItem = bill.serviceItem; // 服务项目
        entity.merchantId = bill.merchantId; // 商户
        entity.agentId = bill.agentId; // 代理
        entity.secondAgentId = bill.secondAgentId; // 二级代理
        entity.orderNo = bill.orderNo; // 订单号
        entity.originBillNo = bill.originBillNo; // 原单号
        entity.tradeAmount = bill.tradeAmount; // 金额
        entity.cityCode = bill.cityCode; // 城市
        entity.payChannel = bill.payChannel; // 渠道
        entity.status = status; // 状态
        entity.createTime = LocalDateTime.now(); // 创建时间
        entity.updateTime = LocalDateTime.now(); // 更新时间
        tradeBillRepository.save(entity); // 落库
        shardRouteService.registerBillRoute(bill.billNo, bill.merchantId, bill.billType); // 注册分片路由

        if (status == BillStatus.PENDING.getCode()) { // 可立即清算
            clearanceTaskService.createTask(bill.billNo, bill.merchantId); // 建任务
            triggerClearance(bill.billNo, bill.merchantId); // 触发清算
        }
        businessMetrics.markBillAccepted(bill.billNo, bill.billType);
        return bill; // 返回 DTO
    }

    /** 触发清算：MQ 模式 sendOrderly，否则 sync executeTask */
    private void triggerClearance(String billNo, Long merchantId) {
        if (payMqProperties.isClearanceViaMq()) { // MQ 流水线
            clearanceTaskPublisher.publish(billNo, merchantId); // 有序发 clearance_task
        } else { // 本地同步
            clearanceTaskService.executeTask(billNo, merchantId); // 直接执行
        }
    }

    /** Entity → DTO */
    private TradeBillDTO toDto(TradeBillEntity entity) {
        TradeBillDTO dto = new TradeBillDTO(); // DTO
        dto.billNo = entity.billNo; // 账单号
        dto.billType = entity.billType; // 类型
        dto.businessLine = entity.businessLine; // 业务线
        dto.category = entity.category; // 品类
        dto.serviceItem = entity.serviceItem; // 服务项目
        dto.merchantId = entity.merchantId; // 商户
        dto.agentId = entity.agentId; // 代理
        dto.secondAgentId = entity.secondAgentId; // 二级代理
        dto.orderNo = entity.orderNo; // 订单号
        dto.originBillNo = entity.originBillNo; // 原单
        dto.tradeAmount = entity.tradeAmount; // 金额
        dto.cityCode = entity.cityCode; // 城市
        dto.payChannel = entity.payChannel; // 渠道
        return dto; // 返回
    }
}
