package com.payment.common.shard;

/**
 * 分片常量，与 MQ Queue 数、clearance_task.shard_id 对齐。
 */
public final class ShardConstants {

    public static final int SHARD_COUNT = 16;
    public static final int DB_COUNT = 4;
    public static final int TABLE_COUNT = 4;

    private ShardConstants() {
    }
}
