package com.payment.domain.repository.impl;

import com.payment.domain.entity.MerchantProfileEntity;
import com.payment.domain.mapper.MerchantProfileMapper;
import com.payment.domain.repository.MerchantProfileRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public class MerchantProfileRepositoryImpl implements MerchantProfileRepository {

    private final MerchantProfileMapper merchantProfileMapper;

    public MerchantProfileRepositoryImpl(MerchantProfileMapper merchantProfileMapper) {
        this.merchantProfileMapper = merchantProfileMapper;
    }

    @Override
    public Optional<MerchantProfileEntity> findById(Long merchantId) {
        return Optional.ofNullable(merchantProfileMapper.selectById(merchantId));
    }
}
