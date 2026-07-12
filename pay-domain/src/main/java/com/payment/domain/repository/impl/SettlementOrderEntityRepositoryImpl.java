package com.payment.domain.repository.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.payment.domain.entity.SettlementOrderEntity;
import com.payment.domain.mapper.SettlementOrderMapper;
import com.payment.domain.repository.SettlementOrderEntityRepository;
import com.payment.domain.support.MapperHelper;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public class SettlementOrderEntityRepositoryImpl implements SettlementOrderEntityRepository {

    private final SettlementOrderMapper settlementOrderMapper;

    public SettlementOrderEntityRepositoryImpl(SettlementOrderMapper settlementOrderMapper) {
        this.settlementOrderMapper = settlementOrderMapper;
    }

    @Override
    public SettlementOrderEntity save(SettlementOrderEntity entity) {
        return MapperHelper.save(settlementOrderMapper, entity);
    }

    @Override
    public Optional<SettlementOrderEntity> findBySettleNo(String settleNo) {
        return Optional.ofNullable(settlementOrderMapper.selectOne(
                new QueryWrapper<SettlementOrderEntity>().eq("settle_no", settleNo)));
    }

    @Override
    public List<SettlementOrderEntity> findByMerchantIdAndStatus(Long merchantId, Integer status) {
        return settlementOrderMapper.selectList(new QueryWrapper<SettlementOrderEntity>()
                .eq("merchant_id", merchantId)
                .eq("status", status));
    }

    @Override
    public List<SettlementOrderEntity> findByStatus(Integer status) {
        return settlementOrderMapper.selectList(new QueryWrapper<SettlementOrderEntity>().eq("status", status));
    }

    @Override
    public List<SettlementOrderEntity> findByMerchantIdAndStatusAndUpdateTimeBetween(
            Long merchantId, Integer status, LocalDateTime start, LocalDateTime end) {
        return settlementOrderMapper.selectList(new QueryWrapper<SettlementOrderEntity>()
                .eq("merchant_id", merchantId)
                .eq("status", status)
                .between("update_time", start, end));
    }

    @Override
    public boolean existsByOriginSettleNo(String originSettleNo) {
        return settlementOrderMapper.selectCount(new QueryWrapper<SettlementOrderEntity>()
                .eq("origin_settle_no", originSettleNo)) > 0;
    }

    @Override
    public boolean existsByOriginSettleNoAndStatusNot(String originSettleNo, Integer status) {
        return settlementOrderMapper.selectCount(new QueryWrapper<SettlementOrderEntity>()
                .eq("origin_settle_no", originSettleNo)
                .ne("status", status)) > 0;
    }
}
