package com.payment.domain.repository;

import com.payment.domain.entity.WithdrawApplyEntity;

import java.util.List;
import java.util.Optional;

/**
 * 提现申请仓储接口
 */
public interface WithdrawApplyRepository {

    /** 保存或更新提现申请 */
    WithdrawApplyEntity save(WithdrawApplyEntity entity);

    /** 查询全部提现申请 */
    List<WithdrawApplyEntity> findAll();

    /** 按结算单号与商户 ID 查询申请 */
    Optional<WithdrawApplyEntity> findBySettleNoAndMerchantId(String settleNo, Long merchantId);

    /** 按结算单号与商户 ID 更新状态 */
    int updateStatusBySettleNoAndMerchantId(String settleNo, Long merchantId, Integer status);
}
