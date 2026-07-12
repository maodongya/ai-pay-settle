package com.payment.api.service; // API 服务接口所在包

import com.payment.api.dto.TradeBillDTO; // 交易账单 DTO

/**
 * 账单接入服务接口，提供账单提交能力
 */
public interface BillAccessService {
    /**
     * 提交交易账单
     *
     * @param bill 账单数据
     * @return 提交后的账单信息
     */
    TradeBillDTO submitBill(TradeBillDTO bill);
}
