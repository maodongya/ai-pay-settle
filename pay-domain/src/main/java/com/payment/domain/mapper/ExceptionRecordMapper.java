package com.payment.domain.mapper; // Mapper 包

import com.baomidou.mybatisplus.core.mapper.BaseMapper; // MyBatis-Plus 基础 Mapper
import com.payment.domain.entity.ExceptionRecordEntity; // 实体
import org.apache.ibatis.annotations.Mapper; // MyBatis 注解

/**
 * 异常工单表 Mapper。
 */
@Mapper // 扫描注册
public interface ExceptionRecordMapper extends BaseMapper<ExceptionRecordEntity> {
    // 使用 BaseMapper 自带 CRUD
}
