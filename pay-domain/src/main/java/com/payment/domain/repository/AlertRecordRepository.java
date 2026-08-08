package com.payment.domain.repository;

import com.payment.domain.entity.AlertRecordEntity;

/**
 * 告警记录仓储接口
 */
public interface AlertRecordRepository {

    /** 保存或更新告警记录 */
    AlertRecordEntity save(AlertRecordEntity entity);

    /** 未处理告警数 */
    long countOpen();
}
