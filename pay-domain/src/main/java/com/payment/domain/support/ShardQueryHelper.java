package com.payment.domain.support;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.payment.common.shard.ShardConstants;
import com.payment.domain.service.ShardRouteService;

import java.util.Optional;
import java.util.function.Consumer;

/**
 * 分片查询辅助：在 WHERE 中补充 merchant_id 以精准路由数据分片。
 */
public final class ShardQueryHelper {

    private ShardQueryHelper() {
    }

    /** 按账单号查询，有路由时补 merchant_id 条件 */
    public static <T> QueryWrapper<T> byBillNo(QueryWrapper<T> wrapper, String billNo, ShardRouteService shardRouteService) {
        Optional<Long> merchantId = shardRouteService.findMerchantIdByBillNo(billNo);
        merchantId.ifPresent(id -> wrapper.eq("merchant_id", id));
        return wrapper.eq("bill_no", billNo);
    }

    /** 按结算单号查询，有路由时补 merchant_id 条件 */
    public static <T> QueryWrapper<T> bySettleNo(QueryWrapper<T> wrapper, String settleNo, ShardRouteService shardRouteService) {
        Optional<Long> merchantId = shardRouteService.findMerchantIdBySettleNo(settleNo);
        merchantId.ifPresent(id -> wrapper.eq("merchant_id", id));
        return wrapper.eq("settle_no", settleNo);
    }

    /** 按账单号查询并回调 merchant_id（供更新场景使用） */
    public static <T> QueryWrapper<T> withBillRoute(QueryWrapper<T> wrapper, String billNo,
                                                      ShardRouteService shardRouteService,
                                                      Consumer<Long> merchantIdConsumer) {
        shardRouteService.findMerchantIdByBillNo(billNo).ifPresent(merchantId -> {
            wrapper.eq("merchant_id", merchantId);
            merchantIdConsumer.accept(merchantId);
        });
        return wrapper.eq("bill_no", billNo);
    }

    /** 按 merchant_id 取模过滤分片（Job 扫描无 shard_id 列的表时使用） */
    public static <T> QueryWrapper<T> eqShardId(QueryWrapper<T> wrapper, int shardId) {
        return wrapper.apply("MOD(merchant_id, {0}) = {1}", ShardConstants.SHARD_COUNT, shardId);
    }
}
