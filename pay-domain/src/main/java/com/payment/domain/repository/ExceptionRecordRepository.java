package com.payment.domain.repository; // 仓储接口包

import com.payment.domain.entity.ExceptionRecordEntity; // 实体

import java.util.Optional; // 可选结果

/**
 * 异常工单仓储接口。
 */
public interface ExceptionRecordRepository {

    /** 保存或更新工单 */
    ExceptionRecordEntity save(ExceptionRecordEntity entity);

    /** 按业务键与异常码查询未关闭工单（幂等建单） */
    Optional<ExceptionRecordEntity> findOpenByBizKeyAndCode(String bizKey, String exceptionCode);
}
