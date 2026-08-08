package com.payment.common.cache;

/**
 * Spring Cache 名称常量。业务代码只引用此处，禁止字面量。
 */
public final class CacheNames {

    public static final String FEE_RULES = "feeRules";
    public static final String AGENT_RELATION = "agentRelation";
    public static final String MERCHANT_PROFILE = "merchantProfile";
    public static final String MERCHANT_CONTRACT = "merchantContract";

    private CacheNames() {
    }
}
