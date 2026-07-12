package com.payment.api.service; // API 服务接口所在包

import com.payment.api.dto.SettleAccountDTO; // 结算账户 DTO
import com.payment.api.dto.WithdrawApplyDTO; // 提现申请 DTO
import com.payment.api.dto.WithdrawResultDTO; // 提现结果 DTO
import com.payment.api.dto.PaymentCallbackDTO; // 支付回调 DTO

import java.math.BigDecimal; // 高精度金额类型
import java.time.LocalDate; // 本地日期类型

/**
 * 结算账户服务接口，提供余额操作、提现及批量结算能力
 */
public interface SettleAccountService {
    /**
     * 增加商户待结算余额
     *
     * @param merchantId 商户 ID
     * @param billNo     关联账单号
     * @param amount     入账金额
     */
    void creditBalance(Long merchantId, String billNo, BigDecimal amount);

    /**
     * 退款扣减商户余额
     *
     * @param merchantId 商户 ID
     * @param billNo     关联账单号
     * @param amount     扣减金额
     */
    void debitRefundBalance(Long merchantId, String billNo, BigDecimal amount);

    /**
     * 查询商户结算账户余额
     *
     * @param merchantId 商户 ID
     * @return 结算账户信息
     */
    SettleAccountDTO queryBalance(Long merchantId);

    /**
     * 提交提现申请
     *
     * @param request 提现申请参数
     * @return 提现处理结果
     */
    WithdrawResultDTO applyWithdraw(WithdrawApplyDTO request);

    /**
     * 处理支付渠道回调
     *
     * @param callback 回调数据
     */
    void handlePaymentCallback(PaymentCallbackDTO callback);

    /** T1 批量结算，batchDate 为运行日 */
    void runT1Batch(LocalDate batchDate);

    /** 重试失败打款 */
    void retryFailedPayments(int limit);
}
