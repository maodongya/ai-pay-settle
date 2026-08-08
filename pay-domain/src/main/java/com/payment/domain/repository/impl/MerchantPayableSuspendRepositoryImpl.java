package com.payment.domain.repository.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.payment.domain.entity.MerchantPayableSuspendEntity;
import com.payment.domain.mapper.MerchantPayableSuspendMapper;
import com.payment.domain.repository.MerchantPayableSuspendRepository;
import com.payment.domain.support.MapperHelper;
import org.springframework.stereotype.Repository;

import java.math.BigDecimal;
import java.util.List;

/**
 * {@link MerchantPayableSuspendRepository} 的 MyBatis-Plus 实现。
 */
@Repository
public class MerchantPayableSuspendRepositoryImpl implements MerchantPayableSuspendRepository {

    private final MerchantPayableSuspendMapper merchantPayableSuspendMapper;

    public MerchantPayableSuspendRepositoryImpl(MerchantPayableSuspendMapper merchantPayableSuspendMapper) {
        this.merchantPayableSuspendMapper = merchantPayableSuspendMapper;
    }

    @Override
    public MerchantPayableSuspendEntity save(MerchantPayableSuspendEntity entity) {
        return MapperHelper.save(merchantPayableSuspendMapper, entity);
    }

    @Override
    public List<MerchantPayableSuspendEntity> findByMerchantIdAndStatusOrderByCreateTimeAsc(
            Long merchantId, Integer status) {
        return merchantPayableSuspendMapper.selectList(new QueryWrapper<MerchantPayableSuspendEntity>()
                .eq("merchant_id", merchantId)
                .eq("status", status)
                .orderByAsc("create_time"));
    }

    @Override
    public boolean applySettlementOffset(Long id, Long merchantId, BigDecimal deduct) {
        return merchantPayableSuspendMapper.applySettlementOffset(id, merchantId, deduct) > 0;
    }
}
