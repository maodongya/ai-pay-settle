package com.payment.common.shard;

/**
 * 分片路由工具：merchant_id → shard_id / db_index / table_suffix。
 */
public final class ShardRouter {

    private ShardRouter() {
    }

    public static int shardId(long merchantId) {
        return (int) (Math.floorMod(merchantId, ShardConstants.SHARD_COUNT));
    }

    public static int dbIndex(long merchantId) {
        return shardId(merchantId) / ShardConstants.TABLE_COUNT;
    }

    public static int tableSuffix(long merchantId) {
        return shardId(merchantId) % ShardConstants.TABLE_COUNT;
    }

    public static String dataSourceName(long merchantId) {
        return "ds_" + dbIndex(merchantId);
    }

    public static String physicalTableName(String logicTable, long merchantId) {
        return logicTable + "_" + tableSuffix(merchantId);
    }
}
