package com.payment.domain.repository.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.payment.domain.entity.ClearanceTaskEntity;
import com.payment.domain.mapper.ClearanceTaskMapper;
import com.payment.domain.repository.ClearanceTaskRepository;
import com.payment.domain.support.MapperHelper;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

@Repository
public class ClearanceTaskRepositoryImpl implements ClearanceTaskRepository {

    private final ClearanceTaskMapper clearanceTaskMapper;

    public ClearanceTaskRepositoryImpl(ClearanceTaskMapper clearanceTaskMapper) {
        this.clearanceTaskMapper = clearanceTaskMapper;
    }

    @Override
    public ClearanceTaskEntity save(ClearanceTaskEntity entity) {
        return MapperHelper.save(clearanceTaskMapper, entity);
    }

    @Override
    public Optional<ClearanceTaskEntity> findByBillNo(String billNo) {
        return Optional.ofNullable(clearanceTaskMapper.selectOne(
                new QueryWrapper<ClearanceTaskEntity>().eq("bill_no", billNo)));
    }

    @Override
    public List<ClearanceTaskEntity> findByStatusOrderByCreateTimeAsc(Integer status) {
        return clearanceTaskMapper.selectList(new QueryWrapper<ClearanceTaskEntity>()
                .eq("status", status)
                .orderByAsc("create_time"));
    }

    @Override
    @Transactional
    public int claimTask(String billNo, Integer expectedStatus, Integer newStatus, LocalDateTime now) {
        return clearanceTaskMapper.claimTask(billNo, expectedStatus, newStatus, now);
    }

    @Override
    public List<ClearanceTaskEntity> findByStatusAndRetryCountLessThan(Integer status, Integer maxRetry) {
        return clearanceTaskMapper.selectList(new QueryWrapper<ClearanceTaskEntity>()
                .eq("status", status)
                .lt("retry_count", maxRetry));
    }

    @Override
    public List<ClearanceTaskEntity> findByStatusAndUpdateTimeBefore(Integer status, LocalDateTime before) {
        return clearanceTaskMapper.selectList(new QueryWrapper<ClearanceTaskEntity>()
                .eq("status", status)
                .lt("update_time", before));
    }
}
