package com.payment.access.service; // 账单接入服务包

import com.baomidou.dynamic.datasource.annotation.DSTransactional;
import com.payment.api.dto.TradeBillDTO; // 账单 DTO
import com.payment.api.dto.ValidateResult; // 校验结果
import com.payment.api.service.BillAccessService; // 接入服务接口
import com.payment.api.service.ClearanceTaskService; // 清算任务服务
import com.payment.api.service.MerchantValidateService; // 商户校验
import com.payment.calc.support.ClearanceTaskPublisher; // 清算 MQ 发布（同模块经 pay-calc 传递）
import com.payment.common.enums.BillStatus; // 账单状态
import com.payment.common.enums.BillType; // 账单类型
import com.payment.common.exception.BizException; // 业务异常
import com.payment.common.metrics.PayBusinessMetrics;
import com.payment.common.ratelimit.DbRateLimit;
import com.payment.common.ratelimit.DbRateLimitLayer;
import com.payment.domain.entity.TradeBillEntity; // 账单实体
import com.payment.domain.repository.TradeBillRepository; // 账单仓储
import com.payment.domain.service.ShardRouteService;
import com.payment.mq.config.PayMqProperties; // MQ 配置
import com.payment.mq.support.MqBacklogState; // 积压熔断状态
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service; // 服务注解

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
    private final BillAccessServiceImpl self; // 代理自调用，保证 @DSTransactional 生效

    /** 构造注入 */
    public BillAccessServiceImpl(TradeBillRepository tradeBillRepository,
                                 MerchantValidateService merchantValidateService,
                                 ClearanceTaskService clearanceTaskService,
                                 PayMqProperties payMqProperties,
                                 ClearanceTaskPublisher clearanceTaskPublisher,
                                 MqBacklogState mqBacklogState,
                                 PayBusinessMetrics businessMetrics,
                                 ShardRouteService shardRouteService,
                                 @Lazy BillAccessServiceImpl self) {
        this.tradeBillRepository = tradeBillRepository; // 账单仓储
        this.merchantValidateService = merchantValidateService; // 校验服务
        this.clearanceTaskService = clearanceTaskService; // 清算服务
        this.payMqProperties = payMqProperties; // MQ 配置
        this.clearanceTaskPublisher = clearanceTaskPublisher; // 发布器
        this.mqBacklogState = mqBacklogState; // 熔断状态
        this.businessMetrics = businessMetrics; // 指标
        this.shardRouteService = shardRouteService; // 分片路由
        this.self = self;
    }

    /**
     * 提交交易账单，已存在则幂等返回。
     * DB 事务与 MQ/同步清算触发分离，避免长事务持锁。
     */
    @Override
    public TradeBillDTO submitBill(TradeBillDTO bill) {
        if (mqBacklogState.isCircuitOpen() && payMqProperties.isEnabled()) { // 积压熔断打开
            throw new BizException(503, "service overloaded, retry later: " + mqBacklogState.getLastSummary());
        }
        // 幂等先查 pay_config.bill_route，避免新单在 data 层无 merchant_id 时广播扫 16 分片
        if (shardRouteService.findMerchantIdByBillNo(bill.billNo).isPresent()) {
            return tradeBillRepository.findByBillNo(bill.billNo)
                    .map(this::toDto)
                    .orElse(bill);
        }
        ValidateResult validation = merchantValidateService.validateBill(bill);
        if (!validation.valid) {
            throw new BizException(validation.errorCode, validation.message);
        }
        int status = self.persistNewBill(bill);
        if (status == BillStatus.PENDING.getCode()) {
            clearanceTaskService.createTask(bill.billNo, bill.merchantId);
            triggerClearance(bill.billNo, bill.merchantId);
        }
        businessMetrics.markBillAccepted(bill.billNo, bill.billType);
        return bill;
    }

    /**
     * 仅落库：trade_bill + bill_route（短事务；校验与 clearance_task 在事务外）。
     */
    @DSTransactional
    @DbRateLimit(layer = DbRateLimitLayer.ACCESS)
    public int persistNewBill(TradeBillDTO bill) {
        int status = BillStatus.PENDING.getCode();
        if (bill.billType == BillType.REFUND.getCode()) {
            TradeBillEntity origin = tradeBillRepository.findByBillNo(bill.originBillNo).orElseThrow();
            if (origin.status != BillStatus.CLEARED.getCode()) {
                status = BillStatus.WAIT_ORIGIN.getCode();
            }
        }

        TradeBillEntity entity = new TradeBillEntity();
        entity.billNo = bill.billNo;
        entity.billType = bill.billType;
        entity.businessLine = bill.businessLine;
        entity.category = bill.category;
        entity.serviceItem = bill.serviceItem;
        entity.merchantId = bill.merchantId;
        entity.agentId = bill.agentId;
        entity.secondAgentId = bill.secondAgentId;
        entity.orderNo = bill.orderNo;
        entity.originBillNo = bill.originBillNo;
        entity.tradeAmount = bill.tradeAmount;
        entity.cityCode = bill.cityCode;
        entity.payChannel = bill.payChannel;
        entity.status = status;
        entity.createTime = LocalDateTime.now();
        entity.updateTime = LocalDateTime.now();
        tradeBillRepository.save(entity);
        shardRouteService.registerBillRoute(bill.billNo, bill.merchantId, bill.billType);
        return status;
    }

    /** 触发清算：MQ 模式 sendOrderly，否则 sync executeTask（事务外） */
    private void triggerClearance(String billNo, Long merchantId) {
        if (payMqProperties.isClearanceViaMq()) {
            clearanceTaskPublisher.publish(billNo, merchantId);
        } else {
            clearanceTaskService.executeTask(billNo, merchantId);
        }
    }

    /** Entity → DTO */
    private TradeBillDTO toDto(TradeBillEntity entity) {
        TradeBillDTO dto = new TradeBillDTO();
        dto.billNo = entity.billNo;
        dto.billType = entity.billType;
        dto.businessLine = entity.businessLine;
        dto.category = entity.category;
        dto.serviceItem = entity.serviceItem;
        dto.merchantId = entity.merchantId;
        dto.agentId = entity.agentId;
        dto.secondAgentId = entity.secondAgentId;
        dto.orderNo = entity.orderNo;
        dto.originBillNo = entity.originBillNo;
        dto.tradeAmount = entity.tradeAmount;
        dto.cityCode = entity.cityCode;
        dto.payChannel = entity.payChannel;
        return dto;
    }
}
