package com.payment.domain.repository.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.payment.domain.entity.AccountFlowEntity;
import com.payment.domain.mapper.AccountFlowMapper;
import com.payment.domain.repository.AccountFlowRepository;
import com.payment.domain.support.MapperHelper;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public class AccountFlowRepositoryImpl implements AccountFlowRepository {

    private final AccountFlowMapper accountFlowMapper;

    public AccountFlowRepositoryImpl(AccountFlowMapper accountFlowMapper) {
        this.accountFlowMapper = accountFlowMapper;
    }

    @Override
    public AccountFlowEntity save(AccountFlowEntity entity) {
        return MapperHelper.save(accountFlowMapper, entity);
    }

    @Override
    public boolean existsByBillNoAndOpType(String billNo, Integer opType) {
        return accountFlowMapper.selectCount(new QueryWrapper<AccountFlowEntity>()
                .eq("bill_no", billNo)
                .eq("op_type", opType)) > 0;
    }

    @Override
    public boolean existsBySettleNoAndOpType(String settleNo, Integer opType) {
        return accountFlowMapper.selectCount(new QueryWrapper<AccountFlowEntity>()
                .eq("settle_no", settleNo)
                .eq("op_type", opType)) > 0;
    }

    @Override
    public List<AccountFlowEntity> findByMerchantIdAndCreateTimeBetween(
            Long merchantId, LocalDateTime start, LocalDateTime end) {
        return accountFlowMapper.selectList(new QueryWrapper<AccountFlowEntity>()
                .eq("merchant_id", merchantId)
                .between("create_time", start, end));
    }
}
