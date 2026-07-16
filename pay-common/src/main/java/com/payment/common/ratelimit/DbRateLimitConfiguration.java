package com.payment.common.ratelimit;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.EnableAspectJAutoProxy;

/**
 * 注册 DB 层级限流切面与配置。
 */
@Configuration
@EnableAspectJAutoProxy
@EnableConfigurationProperties(DbRateLimitProperties.class)
@ConditionalOnProperty(name = "pay.db-rate-limit.enabled", havingValue = "true", matchIfMissing = true)
public class DbRateLimitConfiguration {

    @Bean
    DbRateLimitRegistry dbRateLimitRegistry(DbRateLimitProperties properties) {
        return new DbRateLimitRegistry(properties);
    }

    @Bean
    DbRateLimitAspect dbRateLimitAspect(DbRateLimitRegistry registry) {
        return new DbRateLimitAspect(registry);
    }
}
