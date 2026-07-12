package com.payment.api.dto; // API 数据传输对象所在包

import java.math.BigDecimal; // 高精度金额类型
import java.time.LocalDateTime; // 日期时间类型

/**
 * 费率规则提交 DTO，封装规则配置参数
 */
public class FeeRuleSubmitDTO {
    public String ruleName; // 规则名称
    public Integer targetType; // 目标类型
    public String businessLine; // 业务线
    public String category; // 品类
    public String serviceItem; // 服务项目
    public String cityCode; // 城市编码
    public Integer shareMode; // 分润模式
    public BigDecimal firstMonthValue; // 首月分润值
    public BigDecimal stepDownVal; // 阶梯递减值
    public BigDecimal minShare; // 最低分润
    public LocalDateTime validStart; // 生效开始时间
    public Long submitterId; // 提交人 ID
}
