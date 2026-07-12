package com.payment.domain.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.payment.domain.entity.ClearanceTaskEntity;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

import java.time.LocalDateTime;

@Mapper
public interface ClearanceTaskMapper extends BaseMapper<ClearanceTaskEntity> {

    @Update("UPDATE clearance_task SET status = #{newStatus}, update_time = #{now} WHERE bill_no = #{billNo} AND status = #{expectedStatus}")
    int claimTask(@Param("billNo") String billNo,
                  @Param("expectedStatus") Integer expectedStatus,
                  @Param("newStatus") Integer newStatus,
                  @Param("now") LocalDateTime now);
}
