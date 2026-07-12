package com.payment.domain.repository;

import com.payment.domain.entity.MerchantPayableSuspendEntity;

import java.util.List;

/**
 * 商户应付挂账仓储接口
 */
public interface MerchantPayableSuspendRepository {

    MerchantPayableSuspendEntity save(MerchantPayableSuspendEntity entity);

    List<MerchantPayableSuspendEntity> findByMerchantIdAndStatusOrderByCreateTimeAsc(Long merchantId, Integer status);
}
