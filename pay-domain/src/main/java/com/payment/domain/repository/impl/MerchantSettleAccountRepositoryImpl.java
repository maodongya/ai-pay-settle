package com.payment.domain.repository.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.payment.domain.entity.MerchantSettleAccountEntity;
import com.payment.domain.mapper.MerchantSettleAccountMapper;
import com.payment.domain.repository.MerchantSettleAccountRepository;
import com.payment.domain.support.MapperHelper;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public class MerchantSettleAccountRepositoryImpl implements MerchantSettleAccountRepository {

    private final MerchantSettleAccountMapper merchantSettleAccountMapper;

    public MerchantSettleAccountRepositoryImpl(MerchantSettleAccountMapper merchantSettleAccountMapper) {
        this.merchantSettleAccountMapper = merchantSettleAccountMapper;
    }

    @Override
    public MerchantSettleAccountEntity save(MerchantSettleAccountEntity entity) {
        return MapperHelper.save(merchantSettleAccountMapper, entity);
    }

    @Override
    public Optional<MerchantSettleAccountEntity> findByMerchantId(Long merchantId) {
        return Optional.ofNullable(merchantSettleAccountMapper.selectOne(
                new QueryWrapper<MerchantSettleAccountEntity>().eq("merchant_id", merchantId)));
    }

    @Override
    public boolean updateBalanceCas(Long merchantId, BigDecimal waitDelta, BigDecimal frozenDelta,
                                    Integer expectedVersion, LocalDateTime now) {
        return merchantSettleAccountMapper.updateBalanceCas(
                merchantId, waitDelta, frozenDelta, expectedVersion, now) > 0;
    }

    @Override
    public boolean updateFrozenCas(Long merchantId, BigDecimal frozenDelta, Integer expectedVersion,
                                   LocalDateTime now) {
        return merchantSettleAccountMapper.updateFrozenCas(merchantId, frozenDelta, expectedVersion, now) > 0;
    }

    @Override
    public List<MerchantSettleAccountEntity> findBySettleModeAndWaitBalanceGreaterThanEqualOrderByMerchantIdAsc(
            Integer settleMode, BigDecimal minBalance) {
        return merchantSettleAccountMapper.selectList(new QueryWrapper<MerchantSettleAccountEntity>()
                .eq("settle_mode", settleMode)
                .ge("wait_balance", minBalance)
                .orderByAsc("merchant_id"));
    }

    @Override
    public List<MerchantSettleAccountEntity> findBySettleModeOrderByMerchantIdAsc(Integer settleMode) {
        return merchantSettleAccountMapper.selectList(new QueryWrapper<MerchantSettleAccountEntity>()
                .eq("settle_mode", settleMode)
                .orderByAsc("merchant_id"));
    }

    @Override
    public List<MerchantSettleAccountEntity> findAll() {
        return merchantSettleAccountMapper.selectList(new QueryWrapper<>());
    }
}
