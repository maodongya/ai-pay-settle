package com.payment.domain.repository.impl; // 仓储实现包

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper; // 条件构造
import com.payment.domain.entity.ExceptionRecordEntity; // 实体
import com.payment.domain.mapper.ExceptionRecordMapper; // Mapper
import com.payment.domain.repository.ExceptionRecordRepository; // 接口
import com.payment.domain.support.MapperHelper; // 保存辅助
import org.springframework.stereotype.Repository; // 仓储注解

import java.util.Optional; // Optional

/**
 * 异常工单仓储 MyBatis-Plus 实现。
 */
@Repository // 注册 Bean
public class ExceptionRecordRepositoryImpl implements ExceptionRecordRepository {

    private final ExceptionRecordMapper exceptionRecordMapper; // Mapper 注入

    /** 构造注入 Mapper */
    public ExceptionRecordRepositoryImpl(ExceptionRecordMapper exceptionRecordMapper) {
        this.exceptionRecordMapper = exceptionRecordMapper; // 保存引用
    }

    @Override // 保存
    public ExceptionRecordEntity save(ExceptionRecordEntity entity) {
        return MapperHelper.save(exceptionRecordMapper, entity); //  insert 或 update
    }

    @Override // 查询未关闭工单
    public Optional<ExceptionRecordEntity> findOpenByBizKeyAndCode(String bizKey, String exceptionCode) {
        return Optional.ofNullable(exceptionRecordMapper.selectOne(new QueryWrapper<ExceptionRecordEntity>() // 构造查询
                .eq("biz_key", bizKey) // 业务键
                .eq("exception_code", exceptionCode) // 异常码
                .in("status", 0, 1))); // 待处理或处理中
    }

    @Override
    public long countOpen() {
        return exceptionRecordMapper.selectCount(new QueryWrapper<ExceptionRecordEntity>()
                .in("status", 0, 1));
    }
}
