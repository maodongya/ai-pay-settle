package com.payment.domain.repository.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.payment.domain.entity.SettlementOrderEntity;
import com.payment.domain.mapper.SettlementOrderMapper;
import com.payment.domain.repository.SettlementOrderEntityRepository;
import com.payment.domain.service.ShardRouteService;
import com.payment.domain.support.MapperHelper;
import com.payment.domain.support.ShardQueryHelper;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * {@link SettlementOrderEntityRepository} 的 MyBatis-Plus 实现。
 */
@Repository
public class SettlementOrderEntityRepositoryImpl implements SettlementOrderEntityRepository {

    private final SettlementOrderMapper settlementOrderMapper;
    private final ShardRouteService shardRouteService;

    public SettlementOrderEntityRepositoryImpl(SettlementOrderMapper settlementOrderMapper,
                                                 ShardRouteService shardRouteService) {
        this.settlementOrderMapper = settlementOrderMapper;
        this.shardRouteService = shardRouteService;
    }

    @Override
    public SettlementOrderEntity save(SettlementOrderEntity entity) {
        return MapperHelper.save(settlementOrderMapper, entity);
    }

    /** 通过 settle_route 补全 merchant_id 精准路由 */
    @Override
    public Optional<SettlementOrderEntity> findBySettleNo(String settleNo) {
        QueryWrapper<SettlementOrderEntity> wrapper = new QueryWrapper<>();
        ShardQueryHelper.bySettleNo(wrapper, settleNo, shardRouteService);
        return Optional.ofNullable(settlementOrderMapper.selectOne(wrapper));
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

    /** 通过 settle_route 补全 merchant_id 精准路由 */
    @Override
    public boolean existsByOriginSettleNo(String originSettleNo) {
        QueryWrapper<SettlementOrderEntity> wrapper = new QueryWrapper<>();
        ShardQueryHelper.bySettleNo(wrapper, originSettleNo, shardRouteService);
        return settlementOrderMapper.selectCount(wrapper) > 0;
    }

    @Override
    public boolean existsByOriginSettleNoAndStatusNot(String originSettleNo, Integer status) {
        QueryWrapper<SettlementOrderEntity> wrapper = new QueryWrapper<>();
        ShardQueryHelper.bySettleNo(wrapper, originSettleNo, shardRouteService);
        wrapper.ne("status", status);
        return settlementOrderMapper.selectCount(wrapper) > 0;
    }
}
