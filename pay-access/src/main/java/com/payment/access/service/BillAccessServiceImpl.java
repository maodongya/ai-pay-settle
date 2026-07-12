package com.payment.access.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.payment.api.dto.TradeBillDTO;
import com.payment.api.dto.ValidateResult;
import com.payment.api.service.BillAccessService;
import com.payment.api.service.ClearanceTaskService;
import com.payment.api.service.MerchantValidateService;
import com.payment.common.enums.BillStatus;
import com.payment.common.enums.BillType;
import com.payment.common.exception.BizException;
import com.payment.domain.entity.TradeBillEntity;
import com.payment.domain.repository.TradeBillRepository;
import com.payment.mq.MqTags;
import com.payment.mq.MqTopics;
import com.payment.mq.PayMqProducer;
import com.payment.mq.config.PayMqProperties;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;

/**
 * 账单接入服务实现，接收交易账单并触发清算流程。
 */
@Service // 注册为 Spring 服务
public class BillAccessServiceImpl implements BillAccessService {

    private final TradeBillRepository tradeBillRepository;
    private final MerchantValidateService merchantValidateService;
    private final ClearanceTaskService clearanceTaskService;
    private final PayMqProducer payMqProducer;
    private final PayMqProperties payMqProperties;
    private final ObjectMapper objectMapper;

    public BillAccessServiceImpl(TradeBillRepository tradeBillRepository,
                                 MerchantValidateService merchantValidateService,
                                 ClearanceTaskService clearanceTaskService,
                                 PayMqProducer payMqProducer,
                                 PayMqProperties payMqProperties,
                                 ObjectMapper objectMapper) {
        this.tradeBillRepository = tradeBillRepository;
        this.merchantValidateService = merchantValidateService;
        this.clearanceTaskService = clearanceTaskService;
        this.payMqProducer = payMqProducer;
        this.payMqProperties = payMqProperties;
        this.objectMapper = objectMapper;
    }

    /**
     * 提交交易账单，已存在则幂等返回。
     */
    @Override // 实现接口方法
    @Transactional // 开启事务
    public TradeBillDTO submitBill(TradeBillDTO bill) {
        return tradeBillRepository.findByBillNo(bill.billNo) // 按账单号查询
                .map(this::toDto) // 存在则转为 DTO
                .orElseGet(() -> createBill(bill)); // 不存在则创建
    }

    /**
     * 创建新账单并触发清算。
     */
    private TradeBillDTO createBill(TradeBillDTO bill) {

        ValidateResult validation = merchantValidateService.validateBill(bill); // 校验账单
        if (!validation.valid) { // 校验失败
            throw new BizException(validation.errorCode, validation.message); // 抛出业务异常
        }

        int status = BillStatus.PENDING.getCode(); // 默认待处理状态
        if (bill.billType == BillType.REFUND.getCode()) { // 退款单
            TradeBillEntity origin = tradeBillRepository.findByBillNo(bill.originBillNo).orElseThrow(); // 查询原单
            if (origin.status != BillStatus.CLEARED.getCode()) { // 原单未清算
                status = BillStatus.WAIT_ORIGIN.getCode(); // 等待原单
            }
        }

        TradeBillEntity entity = new TradeBillEntity(); // 创建账单实体
        entity.billNo = bill.billNo; // 账单号
        entity.billType = bill.billType; // 账单类型
        entity.businessLine = bill.businessLine; // 业务线
        entity.category = bill.category; // 品类
        entity.serviceItem = bill.serviceItem; // 服务项目
        entity.merchantId = bill.merchantId; // 商户 ID
        entity.agentId = bill.agentId; // 一级代理 ID
        entity.secondAgentId = bill.secondAgentId; // 二级代理 ID
        entity.orderNo = bill.orderNo; // 订单号
        entity.originBillNo = bill.originBillNo; // 原单号
        entity.tradeAmount = bill.tradeAmount; // 交易金额
        entity.cityCode = bill.cityCode; // 城市编码
        entity.payChannel = bill.payChannel; // 支付渠道
        entity.status = status; // 账单状态
        entity.createTime = LocalDateTime.now(); // 创建时间
        entity.updateTime = LocalDateTime.now(); // 更新时间
        tradeBillRepository.save(entity); // 保存账单

        if (status == BillStatus.PENDING.getCode()) {
            clearanceTaskService.createTask(bill.billNo, bill.merchantId);
            triggerClearance(bill.billNo, bill.merchantId);
        }

        return bill;
    }

    private void triggerClearance(String billNo, Long merchantId) {
        if (payMqProperties.isClearanceViaMq()) {
            Map<String, Object> payload = new HashMap<>();
            payload.put("version", "1.0");
            payload.put("billNo", billNo);
            payload.put("merchantId", merchantId);
            payload.put("shardId", (int) (merchantId % 16));
            payload.put("createTime", LocalDateTime.now().toString());
            try {
                payMqProducer.send(MqTopics.CLEARANCE_TASK, MqTags.TASK, billNo, objectMapper.writeValueAsString(payload));
            } catch (JsonProcessingException e) {
                throw new IllegalStateException(e);
            }
        } else {
            clearanceTaskService.executeTask(billNo);
        }
    }

    /**
     * 将账单实体转换为 DTO。
     */
    private TradeBillDTO toDto(TradeBillEntity entity) {
        TradeBillDTO dto = new TradeBillDTO(); // 创建 DTO
        dto.billNo = entity.billNo; // 账单号
        dto.billType = entity.billType; // 账单类型
        dto.businessLine = entity.businessLine; // 业务线
        dto.category = entity.category; // 品类
        dto.serviceItem = entity.serviceItem; // 服务项目
        dto.merchantId = entity.merchantId; // 商户 ID
        dto.agentId = entity.agentId; // 一级代理 ID
        dto.secondAgentId = entity.secondAgentId; // 二级代理 ID
        dto.orderNo = entity.orderNo; // 订单号
        dto.originBillNo = entity.originBillNo; // 原单号
        dto.tradeAmount = entity.tradeAmount; // 交易金额
        dto.cityCode = entity.cityCode; // 城市编码
        dto.payChannel = entity.payChannel; // 支付渠道
        return dto; // 返回 DTO
    }
}
