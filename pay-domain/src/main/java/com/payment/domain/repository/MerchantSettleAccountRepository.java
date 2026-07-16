package com.payment.domain.repository;

import com.payment.domain.entity.MerchantSettleAccountEntity;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * 商户结算账户仓储接口
 */
public interface MerchantSettleAccountRepository {

    /** 保存或更新结算账户 */
    MerchantSettleAccountEntity save(MerchantSettleAccountEntity entity);

    /** 按商户 ID 查询结算账户 */
    Optional<MerchantSettleAccountEntity> findByMerchantId(Long merchantId);

    /** CAS 更新待结算与冻结余额 */
    boolean updateBalanceCas(Long merchantId, BigDecimal waitDelta, BigDecimal frozenDelta,
                             Integer expectedVersion, LocalDateTime now);

    /** CAS 仅更新冻结余额 */
    boolean updateFrozenCas(Long merchantId, BigDecimal frozenDelta, Integer expectedVersion, LocalDateTime now);

    /** 按结算模式与最低待结算余额查询账户 */
    List<MerchantSettleAccountEntity> findBySettleModeAndWaitBalanceGreaterThanEqualOrderByMerchantIdAsc(
            Integer settleMode, BigDecimal minBalance);

    /** 查询全部结算账户 */
    List<MerchantSettleAccountEntity> findAll();
}
