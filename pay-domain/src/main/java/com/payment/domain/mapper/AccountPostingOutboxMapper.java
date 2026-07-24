package com.payment.domain.mapper;

import com.baomidou.dynamic.datasource.annotation.DS;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.payment.domain.datasource.DataSourceNames;
import com.payment.domain.entity.AccountPostingOutboxEntity;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

@Mapper
@DS(DataSourceNames.DATA)
public interface AccountPostingOutboxMapper extends BaseMapper<AccountPostingOutboxEntity> {

    @Update("UPDATE account_posting_outbox SET status = 1, transaction_no = #{transactionNo}, "
            + "update_time = NOW() "
            + "WHERE id = #{id} AND merchant_id = #{merchantId} AND status = 0")
    int markSuccess(@Param("id") Long id,
                    @Param("merchantId") Long merchantId,
                    @Param("transactionNo") String transactionNo);

    /** 可重试失败：保持 status=0，仅累加 retry_count。 */
    @Update("UPDATE account_posting_outbox SET last_error = #{error}, "
            + "retry_count = retry_count + 1, update_time = NOW() "
            + "WHERE id = #{id} AND merchant_id = #{merchantId} AND status = 0")
    int markRetry(@Param("id") Long id,
                  @Param("merchantId") Long merchantId,
                  @Param("error") String error);

    /** 不可重试或超过次数：status=2。 */
    @Update("UPDATE account_posting_outbox SET status = 2, last_error = #{error}, "
            + "retry_count = retry_count + 1, update_time = NOW() "
            + "WHERE id = #{id} AND merchant_id = #{merchantId} AND status = 0")
    int markFailed(@Param("id") Long id,
                   @Param("merchantId") Long merchantId,
                   @Param("error") String error);
}
