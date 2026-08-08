package com.payment.calc.service;

import com.payment.domain.entity.TradeBillEntity;

/**
 * claim 阶段返回值：携带 merchantId，供 finalize/fail 精确分片路由，避免跨事务 bill_route 查询失败。
 */
public record ClearanceClaimContext(TradeBillEntity bill, Long merchantId) {
}
