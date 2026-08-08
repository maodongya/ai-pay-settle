package com.payment.domain.repository;

import com.payment.domain.entity.MerchantPayableSuspendEntity;

import java.math.BigDecimal;
import java.util.List;

/**
 * 商户应付挂账仓储接口
 */
public interface MerchantPayableSuspendRepository {

    /** 保存或更新挂账记录 */
    MerchantPayableSuspendEntity save(MerchantPayableSuspendEntity entity);

    /** 按商户 ID 与状态查询挂账，按创建时间升序 */
    List<MerchantPayableSuspendEntity> findByMerchantIdAndStatusOrderByCreateTimeAsc(Long merchantId, Integer status);

    /** 结算抵扣挂账金额 */
    boolean applySettlementOffset(Long id, Long merchantId, BigDecimal deduct);
}
