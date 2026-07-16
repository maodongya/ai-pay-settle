package com.payment.domain.repository;

import com.payment.domain.entity.WithdrawApplyEntity;

import java.util.List;
import java.util.Optional;

/**
 * 提现申请仓储接口
 */
public interface WithdrawApplyRepository {

    WithdrawApplyEntity save(WithdrawApplyEntity entity);

    List<WithdrawApplyEntity> findAll();

    Optional<WithdrawApplyEntity> findBySettleNoAndMerchantId(String settleNo, Long merchantId);

    int updateStatusBySettleNoAndMerchantId(String settleNo, Long merchantId, Integer status);
}
