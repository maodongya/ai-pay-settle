package com.payment.domain.repository;

import com.payment.domain.entity.WithdrawApplyEntity;

import java.util.List;

/**
 * 提现申请仓储接口
 */
public interface WithdrawApplyRepository {

    WithdrawApplyEntity save(WithdrawApplyEntity entity);

    List<WithdrawApplyEntity> findAll();
}
