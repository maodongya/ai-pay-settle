package com.payment.domain.repository.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.payment.domain.entity.MerchantContractEntity;
import com.payment.domain.mapper.MerchantContractMapper;
import com.payment.domain.repository.MerchantContractRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public class MerchantContractRepositoryImpl implements MerchantContractRepository {

    private final MerchantContractMapper merchantContractMapper;

    public MerchantContractRepositoryImpl(MerchantContractMapper merchantContractMapper) {
        this.merchantContractMapper = merchantContractMapper;
    }

    @Override
    public Optional<MerchantContractEntity> findByMerchantId(Long merchantId) {
        return Optional.ofNullable(merchantContractMapper.selectOne(
                new QueryWrapper<MerchantContractEntity>().eq("merchant_id", merchantId)));
    }
}
