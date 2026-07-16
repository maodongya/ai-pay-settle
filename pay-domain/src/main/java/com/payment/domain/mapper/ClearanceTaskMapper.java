package com.payment.domain.mapper;

import com.baomidou.dynamic.datasource.annotation.DS;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.payment.domain.datasource.DataSourceNames;
import com.payment.domain.entity.ClearanceTaskEntity;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

import java.time.LocalDateTime;

/**
 * 清算任务表 Mapper，映射 clearance_task 表。
 * 数据源：data 分片库。
 */
@Mapper
@DS(DataSourceNames.DATA)
public interface ClearanceTaskMapper extends BaseMapper<ClearanceTaskEntity> {

    /** 幂等创建任务（bill_no 唯一，已存在则忽略） */
    @Insert("INSERT IGNORE INTO clearance_task (bill_no, merchant_id, shard_id, status, retry_count, "
            + "create_time, update_time) VALUES (#{billNo}, #{merchantId}, #{shardId}, #{status}, 0, "
            + "#{now}, #{now})")
    int insertIgnore(@Param("billNo") String billNo,
                     @Param("merchantId") Long merchantId,
                     @Param("shardId") int shardId,
                     @Param("status") Integer status,
                     @Param("now") LocalDateTime now);

    /** 将 FAILED 任务重置为 PENDING（重试 Job 单 SQL） */
    @Update("UPDATE clearance_task SET status = #{pendingStatus}, update_time = #{now} "
            + "WHERE bill_no = #{billNo} AND merchant_id = #{merchantId} AND status = #{failedStatus} "
            + "AND retry_count < #{maxRetry}")
    int resetToPending(@Param("billNo") String billNo,
                       @Param("merchantId") Long merchantId,
                       @Param("failedStatus") Integer failedStatus,
                       @Param("pendingStatus") Integer pendingStatus,
                       @Param("maxRetry") int maxRetry,
                       @Param("now") LocalDateTime now);

    /** 抢占任务（状态 CAS 更新） */
    @Update("UPDATE clearance_task SET status = #{newStatus}, update_time = #{now} "
            + "WHERE bill_no = #{billNo} AND merchant_id = #{merchantId} AND status = #{expectedStatus}")
    int claimTask(@Param("billNo") String billNo,
                  @Param("merchantId") Long merchantId,
                  @Param("expectedStatus") Integer expectedStatus,
                  @Param("newStatus") Integer newStatus,
                  @Param("now") LocalDateTime now);

    /** 标记任务成功并清空错误信息 */
    @Update("UPDATE clearance_task SET status = #{newStatus}, error_msg = NULL, next_retry_time = NULL, "
            + "update_time = #{now} WHERE bill_no = #{billNo} AND merchant_id = #{merchantId} "
            + "AND status = #{expectedStatus}")
    int markSuccess(@Param("billNo") String billNo,
                    @Param("merchantId") Long merchantId,
                    @Param("expectedStatus") Integer expectedStatus,
                    @Param("newStatus") Integer newStatus,
                    @Param("now") LocalDateTime now);

    /** 标记失败并递增重试次数，超限则置死信 */
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

    /** 强制置为死信状态 */
    @Update("UPDATE clearance_task SET status = #{newStatus}, error_msg = #{errorMsg}, "
            + "next_retry_time = NULL, update_time = #{now} "
            + "WHERE bill_no = #{billNo} AND merchant_id = #{merchantId}")
    int markDead(@Param("billNo") String billNo,
                 @Param("merchantId") Long merchantId,
                 @Param("newStatus") Integer newStatus,
                 @Param("errorMsg") String errorMsg,
                 @Param("now") LocalDateTime now);

    /** 按账单号与商户 ID 查询任务状态 */
    @org.apache.ibatis.annotations.Select("SELECT status FROM clearance_task "
            + "WHERE bill_no = #{billNo} AND merchant_id = #{merchantId} LIMIT 1")
    Integer selectStatusByBillNoAndMerchantId(@Param("billNo") String billNo,
                                              @Param("merchantId") Long merchantId);
}
