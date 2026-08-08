package com.payment.domain.repository;

import com.payment.domain.entity.AccountVoucherEntity;

import java.util.List;

/**
 * 账户凭证仓储接口
 */
public interface AccountVoucherRepository {

    /** 保存或更新单条凭证 */
    AccountVoucherEntity save(AccountVoucherEntity entity);

    /** 批量保存凭证（新记录走批量 INSERT） */
    List<AccountVoucherEntity> saveAll(List<AccountVoucherEntity> entities);

    /** 按账单号判断凭证是否已存在 */
    boolean existsByBillNo(String billNo);
}
