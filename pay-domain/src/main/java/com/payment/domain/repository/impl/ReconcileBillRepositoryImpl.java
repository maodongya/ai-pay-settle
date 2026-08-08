package com.payment.domain.repository.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.payment.domain.entity.ReconcileBillEntity;
import com.payment.domain.mapper.ReconcileBillMapper;
import com.payment.domain.repository.ReconcileBillRepository;
import com.payment.domain.support.MapperHelper;
import org.springframework.stereotype.Repository;

import java.time.LocalDate;
import java.util.Optional;

/**
 * {@link ReconcileBillRepository} 的 MyBatis-Plus 实现。
 */
@Repository
public class ReconcileBillRepositoryImpl implements ReconcileBillRepository {

    private final ReconcileBillMapper reconcileBillMapper;

    public ReconcileBillRepositoryImpl(ReconcileBillMapper reconcileBillMapper) {
        this.reconcileBillMapper = reconcileBillMapper;
    }

    @Override
    public ReconcileBillEntity save(ReconcileBillEntity entity) {
        return MapperHelper.save(reconcileBillMapper, entity);
    }

    @Override
    public Optional<ReconcileBillEntity> findByMerchantIdAndBillDate(Long merchantId, LocalDate billDate) {
        return Optional.ofNullable(reconcileBillMapper.selectOne(
                new QueryWrapper<ReconcileBillEntity>()
                        .eq("merchant_id", merchantId)
                        .eq("bill_date", billDate)));
    }
}
