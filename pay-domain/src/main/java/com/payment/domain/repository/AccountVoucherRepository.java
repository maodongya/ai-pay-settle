package com.payment.domain.repository;

import com.payment.domain.entity.AccountVoucherEntity;

import java.util.List;

/**
 * 账户凭证仓储接口
 */
public interface AccountVoucherRepository {

    AccountVoucherEntity save(AccountVoucherEntity entity);

    List<AccountVoucherEntity> saveAll(List<AccountVoucherEntity> entities);

    boolean existsByBillNo(String billNo);
}
