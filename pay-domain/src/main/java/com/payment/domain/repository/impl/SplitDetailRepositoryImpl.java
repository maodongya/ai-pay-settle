package com.payment.domain.repository.impl;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.payment.domain.entity.SplitDetailEntity;
import com.payment.domain.mapper.SplitDetailMapper;
import com.payment.domain.repository.SplitDetailRepository;
import com.payment.domain.service.ShardRouteService;
import com.payment.domain.support.MapperHelper;
import com.payment.domain.support.ShardQueryHelper;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * {@link SplitDetailRepository} 的 MyBatis-Plus 实现。
 */
@Repository
public class SplitDetailRepositoryImpl implements SplitDetailRepository {

    private final SplitDetailMapper splitDetailMapper;
    private final ShardRouteService shardRouteService;

    public SplitDetailRepositoryImpl(SplitDetailMapper splitDetailMapper,
                                     ShardRouteService shardRouteService) {
        this.splitDetailMapper = splitDetailMapper;
        this.shardRouteService = shardRouteService;
    }

    @Override
    public SplitDetailEntity save(SplitDetailEntity entity) {
        return MapperHelper.save(splitDetailMapper, entity);
    }

    /** 新记录批量 INSERT，已有主键走单条更新 */
    @Override
    public List<SplitDetailEntity> saveAll(List<SplitDetailEntity> entities) {
        if (entities == null || entities.isEmpty()) {
            return entities;
        }
        LocalDateTime now = LocalDateTime.now();
        List<SplitDetailEntity> toInsert = new ArrayList<>(entities.size());
        for (SplitDetailEntity entity : entities) {
            if (entity == null) {
                continue;
            }
            if (entity.id != null) {
                MapperHelper.save(splitDetailMapper, entity);
                continue;
            }
            if (entity.createTime == null) {
                entity.createTime = now;
            }
            toInsert.add(entity);
        }
        if (!toInsert.isEmpty()) {
            splitDetailMapper.insertBatch(toInsert);
        }
        return entities;
    }

    @Override
    public boolean existsByBillNo(String billNo) {
        QueryWrapper<SplitDetailEntity> wrapper = new QueryWrapper<>();
        ShardQueryHelper.byBillNo(wrapper, billNo, shardRouteService);
        return splitDetailMapper.selectCount(wrapper) > 0;
    }

    @Override
    public List<SplitDetailEntity> findByBillNo(String billNo) {
        QueryWrapper<SplitDetailEntity> wrapper = new QueryWrapper<>();
        ShardQueryHelper.byBillNo(wrapper, billNo, shardRouteService);
        return splitDetailMapper.selectList(wrapper);
    }
}
