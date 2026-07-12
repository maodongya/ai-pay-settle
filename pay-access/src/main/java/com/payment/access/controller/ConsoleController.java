package com.payment.access.controller; // 接入层控制器包

import com.payment.api.dto.FeeRuleSubmitDTO; // 费率规则提交 DTO
import com.payment.api.dto.FeeRuleSubmitResultDTO; // 费率规则提交结果 DTO
import com.payment.api.dto.ReconcileBillDTO; // 对账单 DTO
import com.payment.api.service.FeeRuleService; // 费率规则服务接口
import com.payment.api.service.ReconcileService; // 对账服务接口
import com.payment.common.model.ApiResponse; // 统一 API 响应
import org.springframework.format.annotation.DateTimeFormat; // 日期格式化注解
import org.springframework.web.bind.annotation.*; // Web 请求映射注解

import java.time.LocalDate; // 本地日期

/**
 * 控制台 API 控制器，提供费率规则提交和对账单下载接口。
 */
@RestController // REST 控制器
@RequestMapping("/api/v1") // API 路径前缀
public class ConsoleController {

    private final FeeRuleService feeRuleService; // 费率规则服务
    private final ReconcileService reconcileService; // 对账服务

    /**
     * 构造注入依赖。
     */
    public ConsoleController(FeeRuleService feeRuleService, ReconcileService reconcileService) {
        this.feeRuleService = feeRuleService; // 赋值规则服务
        this.reconcileService = reconcileService; // 赋值对账服务
    }

    /**
     * 提交费率规则。
     */
    @PostMapping("/console/fee-rule/submit") // POST 提交规则
    public ApiResponse<FeeRuleSubmitResultDTO> submitFeeRule(@RequestBody FeeRuleSubmitDTO request) {
        return ApiResponse.ok(feeRuleService.submitRule(request)); // 返回提交结果
    }

    /**
     * 下载对账单。
     */
    @GetMapping("/settlement/reconcile/download") // GET 下载对账
    public ApiResponse<ReconcileBillDTO> downloadReconcile(
            @RequestParam Long merchantId, // 商户 ID 参数
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate billDate) { // 对账日期参数
        return ApiResponse.ok(reconcileService.downloadBill(merchantId, billDate)); // 返回对账单
    }
}
