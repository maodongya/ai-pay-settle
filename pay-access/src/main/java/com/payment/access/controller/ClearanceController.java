package com.payment.access.controller; // 接入层控制器包

import com.payment.api.dto.TradeBillDTO; // 交易账单 DTO
import com.payment.api.service.BillAccessService; // 账单接入服务接口
import com.payment.common.model.ApiResponse; // 统一 API 响应
import jakarta.validation.Valid; // 校验注解
import jakarta.validation.constraints.NotNull; // 非空校验
import org.springframework.web.bind.annotation.*; // Web 请求映射注解

import java.math.BigDecimal; // 高精度数值

/**
 * 清算 API 控制器，提供账单提交接口。
 */
@RestController // REST 控制器
@RequestMapping("/api/v1") // API 路径前缀
public class ClearanceController {

    private final BillAccessService billAccessService; // 账单接入服务

    /**
     * 构造注入依赖。
     */
    public ClearanceController(BillAccessService billAccessService) {
        this.billAccessService = billAccessService; // 赋值账单服务
    }

    /**
     * 提交清算账单。
     */
    @PostMapping("/clearance/bill/submit") // POST 提交账单
    public ApiResponse<BillSubmitResponse> submit(@Valid @RequestBody BillSubmitRequest request) {
        TradeBillDTO dto = new TradeBillDTO(); // 创建账单 DTO
        dto.billNo = request.billNo; // 账单号
        dto.billType = request.billType; // 账单类型
        dto.businessLine = request.businessLine; // 业务线
        dto.category = request.category; // 品类
        dto.serviceItem = request.serviceItem; // 服务项目
        dto.merchantId = request.merchantId; // 商户 ID
        dto.agentId = request.agentId; // 一级代理 ID
        dto.secondAgentId = request.secondAgentId; // 二级代理 ID
        dto.orderNo = request.orderNo; // 订单号
        dto.originBillNo = request.originBillNo; // 原单号
        dto.tradeAmount = request.tradeAmount; // 交易金额
        dto.cityCode = request.cityCode; // 城市编码
        dto.payChannel = request.payChannel; // 支付渠道
        billAccessService.submitBill(dto); // 提交账单
        return ApiResponse.ok(new BillSubmitResponse(dto.billNo, 0, true)); // 返回受理结果
    }

    /**
     * 账单提交请求体。
     */
    public static class BillSubmitRequest {
        @NotNull // 非空校验
        public String billNo; // 账单号
        @NotNull // 非空校验
        public Integer billType; // 账单类型
        @NotNull // 非空校验
        public String businessLine; // 业务线
        @NotNull // 非空校验
        public String category; // 品类
        @NotNull // 非空校验
        public String serviceItem; // 服务项目
        @NotNull // 非空校验
        public Long merchantId; // 商户 ID
        public Long agentId; // 一级代理 ID
        public Long secondAgentId; // 二级代理 ID
        @NotNull // 非空校验
        public String orderNo; // 订单号
        public String originBillNo; // 原单号
        @NotNull // 非空校验
        public BigDecimal tradeAmount; // 交易金额
        @NotNull // 非空校验
        public String cityCode; // 城市编码
        public String payChannel; // 支付渠道
    }

    /**
     * 账单提交响应体。
     */
    public record BillSubmitResponse(String billNo, int status, boolean accepted) {
    }
}
