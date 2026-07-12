package com.payment.domain.repository.impl;

import com.payment.domain.entity.AlertRecordEntity;
import com.payment.domain.mapper.AlertRecordMapper;
import com.payment.domain.repository.AlertRecordRepository;
import com.payment.domain.support.MapperHelper;
import org.springframework.stereotype.Repository;

@Repository
public class AlertRecordRepositoryImpl implements AlertRecordRepository {

    private final AlertRecordMapper alertRecordMapper;

    public AlertRecordRepositoryImpl(AlertRecordMapper alertRecordMapper) {
        this.alertRecordMapper = alertRecordMapper;
    }

    @Override
    public AlertRecordEntity save(AlertRecordEntity entity) {
        return MapperHelper.save(alertRecordMapper, entity);
    }
}
