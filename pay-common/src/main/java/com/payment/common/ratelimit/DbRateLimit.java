package com.payment.common.ratelimit;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 业务层 DB 入口 TPS 限流（阻塞等待令牌，平滑消费速率）。
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
public @interface DbRateLimit {

    /** 限流所属层级 */
    DbRateLimitLayer layer();

    /** 每秒许可数；≤0 时回退到配置 pay.db-rate-limit.*-tps */
    double tps() default 0;
}
