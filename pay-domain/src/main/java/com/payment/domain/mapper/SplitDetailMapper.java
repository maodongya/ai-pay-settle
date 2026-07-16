package com.payment.domain.mapper;

import com.baomidou.dynamic.datasource.annotation.DS;
import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.payment.domain.datasource.DataSourceNames;
import com.payment.domain.entity.SplitDetailEntity;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

import java.util.List;

/**
 * 分账明细表 Mapper，映射 split_detail 表。
 * 数据源：data 分片库。
 */
@Mapper
@DS(DataSourceNames.DATA)
public interface SplitDetailMapper extends BaseMapper<SplitDetailEntity> {

    /**
     * 多值 INSERT（同 merchantId 一批，配合 rewriteBatchedStatements / 单语句减少往返）。
     */
    @Insert("""
            <script>
            INSERT INTO split_detail
              (bill_no, merchant_id, party_type, party_id, amount, direction, create_time)
            VALUES
            <foreach collection="list" item="item" separator=",">
              (#{item.billNo}, #{item.merchantId}, #{item.partyType}, #{item.partyId},
               #{item.amount}, #{item.direction}, #{item.createTime})
            </foreach>
            </script>
            """)
    int insertBatch(@Param("list") List<SplitDetailEntity> list);
}
