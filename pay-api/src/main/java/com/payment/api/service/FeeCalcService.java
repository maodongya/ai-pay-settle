package com.payment.api.service; // API 服务接口所在包

import com.payment.api.dto.FeeCalcDTO; // 费用计算请求 DTO
import com.payment.api.dto.FeeCalcResultDTO; // 费用计算结果 DTO

/**
 * 费用计算服务接口，提供分润费用与退款费用计算
 */
public interface FeeCalcService {
    /**
     * 计算分润费用
     *
     * @param request 费用计算请求
     * @return 费用计算结果
     */
    FeeCalcResultDTO calcShareFee(FeeCalcDTO request);

    /**
     * 计算退款费用
     *
     * @param originBillNo  原账单号
     * @param refundBillNo  退款账单号
     * @param refundAmount  退款金额
     * @return 退款费用计算结果
     */
    FeeCalcResultDTO calcRefundFee(String originBillNo, String refundBillNo, java.math.BigDecimal refundAmount);
}
