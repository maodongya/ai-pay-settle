package com.payment.api.dto; // API 数据传输对象所在包

import java.math.BigDecimal; // 高精度金额类型
import java.time.LocalDate; // 本地日期类型

/**
 * 对账账单 DTO，封装商户日对账汇总信息
 */
public class ReconcileBillDTO {
    public Long merchantId; // 商户 ID
    public LocalDate billDate; // 账单日期
    public BigDecimal totalIncome; // 总收入
    public BigDecimal totalSettle; // 总结算金额
    public String fileUrl; // 对账文件下载地址
    public String content; // 对账文件内容
}
