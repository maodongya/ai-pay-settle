package com.payment.common.util;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * 基于 JVM 计数器的单号生成器（Redis 关闭时的降级实现）。
 */
@Component
@ConditionalOnProperty(prefix = "payment.redis", name = "enabled", havingValue = "false")
public class LocalSeqGenerator implements BizSeqGenerator {

    @Override
    public String billNo() {
        return SeqGenerator.billNo();
    }

    @Override
    public String settleNo() {
        return SeqGenerator.settleNo();
    }

    @Override
    public String applyNo() {
        return SeqGenerator.applyNo();
    }
}
