package com.payment.domain.repository.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.payment.domain.entity.AccountVoucherEntity;
import com.payment.domain.mapper.AccountVoucherMapper;
import com.payment.domain.repository.AccountVoucherRepository;
import com.payment.domain.support.MapperHelper;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public class AccountVoucherRepositoryImpl implements AccountVoucherRepository {

    private final AccountVoucherMapper accountVoucherMapper;

    public AccountVoucherRepositoryImpl(AccountVoucherMapper accountVoucherMapper) {
        this.accountVoucherMapper = accountVoucherMapper;
    }

    @Override
    public AccountVoucherEntity save(AccountVoucherEntity entity) {
        return MapperHelper.save(accountVoucherMapper, entity);
    }

    @Override
    public List<AccountVoucherEntity> saveAll(List<AccountVoucherEntity> entities) {
        return MapperHelper.saveAll(accountVoucherMapper, entities);
    }

    @Override
    public boolean existsByBillNo(String billNo) {
        return accountVoucherMapper.selectCount(new QueryWrapper<AccountVoucherEntity>().eq("bill_no", billNo)) > 0;
    }
}
