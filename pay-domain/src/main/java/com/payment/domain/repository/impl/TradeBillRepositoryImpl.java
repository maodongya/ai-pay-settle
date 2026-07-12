package com.payment.domain.repository.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.payment.domain.entity.TradeBillEntity;
import com.payment.domain.mapper.TradeBillMapper;
import com.payment.domain.repository.TradeBillRepository;
import com.payment.domain.support.MapperHelper;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public class TradeBillRepositoryImpl implements TradeBillRepository {

    private final TradeBillMapper tradeBillMapper;

    public TradeBillRepositoryImpl(TradeBillMapper tradeBillMapper) {
        this.tradeBillMapper = tradeBillMapper;
    }

    @Override
    public TradeBillEntity save(TradeBillEntity entity) {
        return MapperHelper.save(tradeBillMapper, entity);
    }

    @Override
    public Optional<TradeBillEntity> findByBillNo(String billNo) {
        return Optional.ofNullable(tradeBillMapper.selectOne(
                new QueryWrapper<TradeBillEntity>().eq("bill_no", billNo)));
    }

    @Override
    public List<TradeBillEntity> findByStatusAndOriginBillNo(Integer status, String originBillNo) {
        return tradeBillMapper.selectList(new QueryWrapper<TradeBillEntity>()
                .eq("status", status)
                .eq("origin_bill_no", originBillNo));
    }

    @Override
    public List<TradeBillEntity> findByStatus(Integer status) {
        return tradeBillMapper.selectList(new QueryWrapper<TradeBillEntity>().eq("status", status));
    }
}
