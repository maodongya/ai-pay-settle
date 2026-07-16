package com.payment.domain.mapper;

import com.baomidou.dynamic.datasource.annotation.DS;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.payment.domain.datasource.DataSourceNames;
import com.payment.domain.entity.ClearanceTaskEntity;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

import java.time.LocalDateTime;

@Mapper
@DS(DataSourceNames.DATA)
public interface ClearanceTaskMapper extends BaseMapper<ClearanceTaskEntity> {

    @Update("UPDATE clearance_task SET status = #{newStatus}, update_time = #{now} "
            + "WHERE bill_no = #{billNo} AND merchant_id = #{merchantId} AND status = #{expectedStatus}")
    int claimTask(@Param("billNo") String billNo,
                  @Param("merchantId") Long merchantId,
                  @Param("expectedStatus") Integer expectedStatus,
                  @Param("newStatus") Integer newStatus,
                  @Param("now") LocalDateTime now);

    @Update("UPDATE clearance_task SET status = #{newStatus}, error_msg = NULL, next_retry_time = NULL, "
            + "update_time = #{now} WHERE bill_no = #{billNo} AND merchant_id = #{merchantId} "
            + "AND status = #{expectedStatus}")
    int markSuccess(@Param("billNo") String billNo,
                    @Param("merchantId") Long merchantId,
                    @Param("expectedStatus") Integer expectedStatus,
                    @Param("newStatus") Integer newStatus,
                    @Param("now") LocalDateTime now);

    @Update("UPDATE clearance_task SET retry_count = retry_count + 1, "
            + "status = IF(retry_count + 1 >= #{maxRetry}, #{deadStatus}, #{failedStatus}), "
            + "error_msg = #{errorMsg}, next_retry_time = #{nextRetryTime}, update_time = #{now} "
            + "WHERE bill_no = #{billNo} AND merchant_id = #{merchantId} AND status = #{expectedStatus}")
    int markFailed(@Param("billNo") String billNo,
                   @Param("merchantId") Long merchantId,
                   @Param("expectedStatus") Integer expectedStatus,
                   @Param("failedStatus") Integer failedStatus,
                   @Param("deadStatus") Integer deadStatus,
                   @Param("maxRetry") int maxRetry,
                   @Param("errorMsg") String errorMsg,
                   @Param("nextRetryTime") LocalDateTime nextRetryTime,
                   @Param("now") LocalDateTime now);

    @Update("UPDATE clearance_task SET status = #{newStatus}, error_msg = #{errorMsg}, "
            + "next_retry_time = NULL, update_time = #{now} "
            + "WHERE bill_no = #{billNo} AND merchant_id = #{merchantId}")
    int markDead(@Param("billNo") String billNo,
                 @Param("merchantId") Long merchantId,
                 @Param("newStatus") Integer newStatus,
                 @Param("errorMsg") String errorMsg,
                 @Param("now") LocalDateTime now);

    @org.apache.ibatis.annotations.Select("SELECT status FROM clearance_task "
            + "WHERE bill_no = #{billNo} AND merchant_id = #{merchantId} LIMIT 1")
    Integer selectStatusByBillNoAndMerchantId(@Param("billNo") String billNo,
                                              @Param("merchantId") Long merchantId);
}
