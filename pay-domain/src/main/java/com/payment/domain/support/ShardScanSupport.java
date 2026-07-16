package com.payment.domain.support;

import com.payment.common.shard.ShardConstants;

import java.util.ArrayList;
import java.util.List;
import java.util.function.IntFunction;
import java.util.function.IntConsumer;

/**
 * 16 分片并行扫描辅助，供定时 Job 按 shard_id 分片查询，避免全库广播。
 */
public final class ShardScanSupport {

    private ShardScanSupport() {
    }

    /** 遍历 16 个分片并执行回调 */
    public static void forEachShard(IntConsumer action) {
        for (int shardId = 0; shardId < ShardConstants.SHARD_COUNT; shardId++) {
            action.accept(shardId);
        }
    }

    /** 遍历各分片查询并合并结果列表 */
    public static <T> List<T> collectAcrossShards(IntFunction<List<T>> supplier) {
        List<T> merged = new ArrayList<>();
        forEachShard(shardId -> merged.addAll(supplier.apply(shardId)));
        return merged;
    }

    /** 将总限额均摊到各分片，至少 1 条 */
    public static int perShardLimit(int totalLimit) {
        return Math.max(1, (totalLimit + ShardConstants.SHARD_COUNT - 1) / ShardConstants.SHARD_COUNT);
    }
}
