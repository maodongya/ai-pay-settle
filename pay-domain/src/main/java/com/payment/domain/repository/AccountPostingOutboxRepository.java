package com.payment.domain.repository;

import com.payment.domain.entity.AccountPostingOutboxEntity;

import java.util.List;

public interface AccountPostingOutboxRepository {

    AccountPostingOutboxEntity save(AccountPostingOutboxEntity entity);

    List<AccountPostingOutboxEntity> findPending(int limit);

    int markSuccess(Long id, Long merchantId, String transactionNo);

    int markRetry(Long id, Long merchantId, String error);

    int markFailed(Long id, Long merchantId, String error);

    boolean existsByBizKey(String tenantId, String bizNo, String bizType);
}
