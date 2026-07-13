package com.payment.domain.entity; // 实体包声明

import com.baomidou.mybatisplus.annotation.FieldFill;
import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;

import java.math.BigDecimal; // 高精度金额类型
import java.time.LocalDateTime; // 日期时间类型

/**
 * 分账明细实体
 * 对应数据库表 split_detail
 */
@TableName("split_detail") // 映射到 split_detail 表
public class SplitDetailEntity {

    /** 明细主键 ID */
    @TableId(value = "id", type = IdType.AUTO) // 数据库自增主键
    public Long id;

    /** 关联账单号 */
    @TableField("bill_no") // 非空，最大长度 64
    public String billNo;

    @TableField("merchant_id")
    public Long merchantId;

    /** 参与方类型 */
    @TableField("party_type") // 非空，列名 party_type
    public Integer partyType;

    /** 参与方 ID */
    @TableField("party_id") // 非空，列名 party_id
    public Long partyId;

    /** 分账金额 */
    public BigDecimal amount; // 非空，18 位精度 2 位小数

    /** 资金方向（借/贷） */
    public Integer direction; // 非空

    /** 创建时间 */
    @TableField(value = "create_time", fill = FieldFill.INSERT) // 非空，列名 create_time
    public LocalDateTime createTime;
}
