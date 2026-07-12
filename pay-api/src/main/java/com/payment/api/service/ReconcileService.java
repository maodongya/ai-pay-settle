package com.payment.api.service; // API 服务接口所在包

import com.payment.api.dto.ReconcileBillDTO; // 对账账单 DTO

import java.time.LocalDate; // 本地日期类型

/**
 * 对账服务接口，提供日账单生成与下载能力
 */
public interface ReconcileService {
    /**
     * 生成指定日期的日账单
     *
     * @param billDate 账单日期
     */
    void generateDailyBills(LocalDate billDate);

    /**
     * 下载商户指定日期的对账账单
     *
     * @param merchantId 商户 ID
     * @param billDate   账单日期
     * @return 对账账单数据
     */
    ReconcileBillDTO downloadBill(Long merchantId, LocalDate billDate);
}
