package com.payment.domain.repository.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.payment.domain.entity.AccountVoucherEntity;
import com.payment.domain.mapper.AccountVoucherMapper;
import com.payment.domain.repository.AccountVoucherRepository;
import com.payment.domain.support.MapperHelper;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * {@link AccountVoucherRepository} 的 MyBatis-Plus 实现。
 */
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

    /** 新记录批量 INSERT，已有主键走单条更新 */
    @Override
    public List<AccountVoucherEntity> saveAll(List<AccountVoucherEntity> entities) {
        if (entities == null || entities.isEmpty()) {
            return entities;
        }
        LocalDateTime now = LocalDateTime.now();
        List<AccountVoucherEntity> toInsert = new ArrayList<>(entities.size());
        for (AccountVoucherEntity entity : entities) {
            if (entity == null) {
                continue;
            }
            if (entity.voucherId != null) {
                MapperHelper.save(accountVoucherMapper, entity);
                continue;
            }
            if (entity.createTime == null) {
                entity.createTime = now;
            }
            if (entity.syncStatus == null) {
                entity.syncStatus = 0;
            }
            toInsert.add(entity);
        }
        if (!toInsert.isEmpty()) {
            accountVoucherMapper.insertBatch(toInsert);
        }
        return entities;
    }

    @Override
    public boolean existsByBillNo(String billNo) {
        return accountVoucherMapper.selectCount(new QueryWrapper<AccountVoucherEntity>().eq("bill_no", billNo)) > 0;
    }
}
