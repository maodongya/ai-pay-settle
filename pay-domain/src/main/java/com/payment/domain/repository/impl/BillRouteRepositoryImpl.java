package com.payment.domain.repository.impl;

import com.payment.domain.entity.BillRouteEntity;
import com.payment.domain.mapper.BillRouteMapper;
import com.payment.domain.repository.BillRouteRepository;
import org.springframework.stereotype.Repository;

@Repository
public class BillRouteRepositoryImpl implements BillRouteRepository {

    private final BillRouteMapper billRouteMapper;

    public BillRouteRepositoryImpl(BillRouteMapper billRouteMapper) {
        this.billRouteMapper = billRouteMapper;
    }

    @Override
    public BillRouteEntity save(BillRouteEntity entity) {
        // bill_no 为业务主键（IdType.INPUT），已赋值时 MapperHelper 会误走 updateById
        BillRouteEntity existing = billRouteMapper.selectById(entity.billNo);
        if (existing == null) {
            billRouteMapper.insert(entity);
        } else {
            billRouteMapper.updateById(entity);
        }
        return entity;
    }

    @Override
    public java.util.Optional<BillRouteEntity> findByBillNo(String billNo) {
        return java.util.Optional.ofNullable(billRouteMapper.selectById(billNo));
    }
}
