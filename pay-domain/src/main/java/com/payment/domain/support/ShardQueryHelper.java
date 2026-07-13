package com.payment.domain.support;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.payment.domain.service.ShardRouteService;

import java.util.Optional;
import java.util.function.Consumer;

/**
 * 分片查询辅助：在 WHERE 中补充 merchant_id 以精准路由数据分片。
 */
public final class ShardQueryHelper {

    private ShardQueryHelper() {
    }

    public static <T> QueryWrapper<T> byBillNo(QueryWrapper<T> wrapper, String billNo, ShardRouteService shardRouteService) {
        Optional<Long> merchantId = shardRouteService.findMerchantIdByBillNo(billNo);
        merchantId.ifPresent(id -> wrapper.eq("merchant_id", id));
        return wrapper.eq("bill_no", billNo);
    }

    public static <T> QueryWrapper<T> bySettleNo(QueryWrapper<T> wrapper, String settleNo, ShardRouteService shardRouteService) {
        Optional<Long> merchantId = shardRouteService.findMerchantIdBySettleNo(settleNo);
        merchantId.ifPresent(id -> wrapper.eq("merchant_id", id));
        return wrapper.eq("settle_no", settleNo);
    }

    public static <T> QueryWrapper<T> withBillRoute(QueryWrapper<T> wrapper, String billNo,
                                                      ShardRouteService shardRouteService,
                                                      Consumer<Long> merchantIdConsumer) {
        shardRouteService.findMerchantIdByBillNo(billNo).ifPresent(merchantId -> {
            wrapper.eq("merchant_id", merchantId);
            merchantIdConsumer.accept(merchantId);
        });
        return wrapper.eq("bill_no", billNo);
    }
}
