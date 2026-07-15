package com.payment.common.cache;

/**
 * Redis 命令型 Key（序列/锁/幂等）。禁止在业务类中拼接字面量前缀。
 */
public final class RedisKeys {

    public static String seqBill(String yyyyMMdd) {
        return "seq:bill:" + yyyyMMdd;
    }

    public static String seqSettle(String yyyyMMdd) {
        return "seq:settle:" + yyyyMMdd;
    }

    public static String seqWithdraw(String yyyyMMdd) {
        return "seq:withdraw:" + yyyyMMdd;
    }

    public static String settleLock(Long merchantId) {
        return "settle:lock:" + merchantId;
    }

    public static String feeLock(String billNo) {
        return "fee:lock:" + billNo;
    }

    public static String idempotent(String billNo, String op) {
        return "idempotent:" + billNo + ":" + op;
    }

    private RedisKeys() {
    }
}
