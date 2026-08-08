package com.payment.common.shard;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ShardRouterTest {

    @Test
    void shardRoutingMatchesSixteenShardLayout() {
        assertEquals(1, ShardRouter.shardId(10001L));
        assertEquals(0, ShardRouter.dbIndex(10001L));
        assertEquals(1, ShardRouter.tableSuffix(10001L));

        assertEquals(0, ShardRouter.shardId(10016L));
        assertEquals(0, ShardRouter.dbIndex(10016L));
        assertEquals(0, ShardRouter.tableSuffix(10016L));

        assertEquals(1, ShardRouter.shardId(10017L));
        assertEquals(0, ShardRouter.dbIndex(10017L));
        assertEquals(1, ShardRouter.tableSuffix(10017L));
    }
}
