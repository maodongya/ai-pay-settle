package com.payment.domain.repository.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.payment.domain.entity.WithdrawApplyEntity;
import com.payment.domain.mapper.WithdrawApplyMapper;
import com.payment.domain.repository.WithdrawApplyRepository;
import com.payment.domain.support.MapperHelper;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public class WithdrawApplyRepositoryImpl implements WithdrawApplyRepository {

    private final WithdrawApplyMapper withdrawApplyMapper;

    public WithdrawApplyRepositoryImpl(WithdrawApplyMapper withdrawApplyMapper) {
        this.withdrawApplyMapper = withdrawApplyMapper;
    }

    @Override
    public WithdrawApplyEntity save(WithdrawApplyEntity entity) {
        return MapperHelper.save(withdrawApplyMapper, entity);
    }

    @Override
    public List<WithdrawApplyEntity> findAll() {
        return withdrawApplyMapper.selectList(new QueryWrapper<>());
    }

    @Override
    public Optional<WithdrawApplyEntity> findBySettleNoAndMerchantId(String settleNo, Long merchantId) {
        return Optional.ofNullable(withdrawApplyMapper.selectOne(new QueryWrapper<WithdrawApplyEntity>()
                .eq("settle_no", settleNo)
                .eq("merchant_id", merchantId)
                .last("LIMIT 1")));
    }

    @Override
    public int updateStatusBySettleNoAndMerchantId(String settleNo, Long merchantId, Integer status) {
        return withdrawApplyMapper.updateStatusBySettleNoAndMerchantId(settleNo, merchantId, status);
    }
}
