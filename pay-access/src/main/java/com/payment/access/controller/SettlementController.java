package com.payment.access.controller; // 接入层控制器包

import com.payment.api.dto.PaymentCallbackDTO; // 支付回调 DTO
import com.payment.api.dto.SettleAccountDTO; // 结算账户 DTO
import com.payment.api.dto.WithdrawApplyDTO; // 提现申请 DTO
import com.payment.api.dto.WithdrawResultDTO; // 提现结果 DTO
import com.payment.api.service.SettleAccountService; // 结算账户服务接口
import com.payment.common.model.ApiResponse; // 统一 API 响应
import jakarta.validation.constraints.NotNull; // 非空校验
import org.springframework.web.bind.annotation.*; // Web 请求映射注解

import java.math.BigDecimal; // 高精度数值

/**
 * 结算 API 控制器，提供余额查询、提现申请和支付回调接口。
 */
@RestController // REST 控制器
@RequestMapping("/api/v1/settlement") // 结算 API 路径前缀
public class SettlementController {

    private final SettleAccountService settleAccountService; // 结算账户服务

    /**
     * 构造注入依赖。
     */
    public SettlementController(SettleAccountService settleAccountService) {
        this.settleAccountService = settleAccountService; // 赋值结算服务
    }

    /**
     * 查询商户结算账户余额。
     */
    @GetMapping("/account/balance") // GET 查询余额
    public ApiResponse<SettleAccountDTO> balance(@RequestParam Long merchantId) {
        return ApiResponse.ok(settleAccountService.queryBalance(merchantId)); // 返回余额信息
    }

    /**
     * 申请提现。
     */
    @PostMapping("/withdraw/apply") // POST 申请提现
    public ApiResponse<WithdrawResultDTO> withdraw(@RequestBody WithdrawRequest request) {
        WithdrawApplyDTO dto = new WithdrawApplyDTO(); // 创建提现 DTO
        dto.merchantId = request.merchantId; // 商户 ID
        dto.withdrawAmount = request.withdrawAmount; // 提现金额
        dto.settleCardNo = request.settleCardNo; // 结算卡号
        return ApiResponse.ok(settleAccountService.applyWithdraw(dto)); // 返回提现结果
    }

    /**
     * 接收支付渠道回调。
     */
    @PostMapping("/channel/payment/callback") // POST 支付回调
    public ApiResponse<Void> callback(@RequestBody PaymentCallbackDTO callback) {
        settleAccountService.handlePaymentCallback(callback); // 处理回调
        return ApiResponse.ok(null); // 返回成功
    }

    /**
     * 提现申请请求体。
     */
    public static class WithdrawRequest {
        @NotNull // 非空校验
        public Long merchantId; // 商户 ID
        @NotNull // 非空校验
        public BigDecimal withdrawAmount; // 提现金额
        public String settleCardNo; // 结算卡号
    }
}
