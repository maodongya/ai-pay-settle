package com.payment.domain.repository.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.payment.domain.entity.WithdrawApplyEntity;
import com.payment.domain.mapper.WithdrawApplyMapper;
import com.payment.domain.repository.WithdrawApplyRepository;
import com.payment.domain.support.MapperHelper;
import org.springframework.stereotype.Repository;

import java.util.List;

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
}
