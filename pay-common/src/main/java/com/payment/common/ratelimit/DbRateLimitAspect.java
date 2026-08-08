package com.payment.common.ratelimit;

import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;

/**
 * {@link DbRateLimit} 切面：进入 DB 热路径前阻塞获取层级令牌。
 */
@Aspect
public class DbRateLimitAspect {

    private final DbRateLimitRegistry registry;

    /**
     * 构造注入限流注册表。
     */
    public DbRateLimitAspect(DbRateLimitRegistry registry) {
        this.registry = registry;
    }

    /**
     * 环绕通知：进入 DB 热路径前阻塞获取层级令牌。
     */
    @Around("@annotation(limit)")
    public Object around(ProceedingJoinPoint pjp, DbRateLimit limit) throws Throwable {
        registry.acquire(limit.layer(), limit.tps());
        return pjp.proceed();
    }
}
