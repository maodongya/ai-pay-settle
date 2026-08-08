package com.payment.domain.repository.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.payment.common.cache.CacheNames;
import com.payment.domain.entity.MerchantContractEntity;
import com.payment.domain.mapper.MerchantContractMapper;
import com.payment.domain.repository.MerchantContractRepository;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Repository;

import java.util.Optional;

/**
 * {@link MerchantContractRepository} 的 MyBatis-Plus 实现。
 */
@Repository
public class MerchantContractRepositoryImpl implements MerchantContractRepository {

    private final MerchantContractMapper merchantContractMapper;

    public MerchantContractRepositoryImpl(MerchantContractMapper merchantContractMapper) {
        this.merchantContractMapper = merchantContractMapper;
    }

    /** 结果缓存，减少 config 库查询 */
    @Override
    @Cacheable(cacheNames = CacheNames.MERCHANT_CONTRACT, key = "#merchantId", unless = "#result == null")
    public Optional<MerchantContractEntity> findByMerchantId(Long merchantId) {
        return Optional.ofNullable(merchantContractMapper.selectOne(
                new QueryWrapper<MerchantContractEntity>().eq("merchant_id", merchantId)));
    }
}
