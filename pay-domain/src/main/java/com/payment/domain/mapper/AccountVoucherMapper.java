package com.payment.domain.mapper;

import com.baomidou.dynamic.datasource.annotation.DS;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.payment.domain.datasource.DataSourceNames;
import com.payment.domain.entity.AccountVoucherEntity;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 账户凭证表 Mapper，映射 account_voucher 表。
 * 数据源：data 分片库。
 */
@Mapper
@DS(DataSourceNames.DATA)
public interface AccountVoucherMapper extends BaseMapper<AccountVoucherEntity> {

    /**
     * 多值 INSERT（同 merchantId 一批，减少清算路径 SQL 往返）。
     */
    @Insert("""
            <script>
            INSERT INTO account_voucher
              (bill_no, merchant_id, debit_subject, credit_subject, amount, sync_status, create_time)
            VALUES
            <foreach collection="list" item="item" separator=",">
              (#{item.billNo}, #{item.merchantId}, #{item.debitSubject}, #{item.creditSubject},
               #{item.amount}, #{item.syncStatus}, #{item.createTime})
            </foreach>
            </script>
            """)
    int insertBatch(@Param("list") List<AccountVoucherEntity> list);
}
