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

    MerchantSettleAccountEntity save(MerchantSettleAccountEntity entity);

    Optional<MerchantSettleAccountEntity> findByMerchantId(Long merchantId);

    boolean updateBalanceCas(Long merchantId, BigDecimal waitDelta, BigDecimal frozenDelta,
                             Integer expectedVersion, LocalDateTime now);

    boolean updateFrozenCas(Long merchantId, BigDecimal frozenDelta, Integer expectedVersion, LocalDateTime now);

    List<MerchantSettleAccountEntity> findBySettleModeAndWaitBalanceGreaterThanEqualOrderByMerchantIdAsc(
            Integer settleMode, BigDecimal minBalance);

    List<MerchantSettleAccountEntity> findAll();
}
