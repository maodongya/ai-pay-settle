package com.payment.domain.repository.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.payment.domain.entity.AccountPostingOutboxEntity;
import com.payment.domain.mapper.AccountPostingOutboxMapper;
import com.payment.domain.repository.AccountPostingOutboxRepository;
import com.payment.domain.support.MapperHelper;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public class AccountPostingOutboxRepositoryImpl implements AccountPostingOutboxRepository {

    private final AccountPostingOutboxMapper accountPostingOutboxMapper;

    public AccountPostingOutboxRepositoryImpl(AccountPostingOutboxMapper accountPostingOutboxMapper) {
        this.accountPostingOutboxMapper = accountPostingOutboxMapper;
    }

    @Override
    public AccountPostingOutboxEntity save(AccountPostingOutboxEntity entity) {
        return MapperHelper.save(accountPostingOutboxMapper, entity);
    }

    @Override
    public List<AccountPostingOutboxEntity> findPending(int limit) {
        return accountPostingOutboxMapper.selectList(new QueryWrapper<AccountPostingOutboxEntity>()
                .eq("status", 0)
                .orderByAsc("create_time")
                .last("LIMIT " + limit));
    }

    @Override
    public int markSuccess(Long id, Long merchantId, String transactionNo) {
        return accountPostingOutboxMapper.markSuccess(id, merchantId, transactionNo);
    }

    @Override
    public int markRetry(Long id, Long merchantId, String error) {
        return accountPostingOutboxMapper.markRetry(id, merchantId, truncate(error, 512));
    }

    @Override
    public int markFailed(Long id, Long merchantId, String error) {
        return accountPostingOutboxMapper.markFailed(id, merchantId, truncate(error, 512));
    }

    @Override
    public boolean existsByBizKey(String tenantId, String bizNo, String bizType) {
        return accountPostingOutboxMapper.selectCount(new QueryWrapper<AccountPostingOutboxEntity>()
                .eq("tenant_id", tenantId)
                .eq("biz_no", bizNo)
                .eq("biz_type", bizType)) > 0;
    }

    private static String truncate(String error, int maxLen) {
        if (error == null) {
            return null;
        }
        return error.length() <= maxLen ? error : error.substring(0, maxLen);
    }
}
