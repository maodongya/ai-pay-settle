package com.payment.common.redis;

import com.fasterxml.jackson.annotation.JsonTypeInfo;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.jsontype.impl.LaissezFaireSubTypeValidator;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.payment.common.cache.CacheNames;
import com.payment.common.cache.PaymentRedisProperties;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.cache.CacheManager;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.cache.concurrent.ConcurrentMapCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.cache.RedisCacheManager;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.RedisSerializationContext;
import org.springframework.data.redis.serializer.StringRedisSerializer;

import java.time.Duration;
import java.util.HashMap;
import java.util.Map;

/**
 * Redis / 本地缓存基础设施。
 */
@Configuration
@EnableConfigurationProperties(PaymentRedisProperties.class)
public class RedisInfrastructureConfig {

    @Configuration
    @EnableCaching
    @ConditionalOnProperty(prefix = "payment.redis", name = "enabled", havingValue = "true", matchIfMissing = true)
    static class RedisEnabledConfig {

        @Bean
        StringRedisTemplate stringRedisTemplate(RedisConnectionFactory factory) {
            return new StringRedisTemplate(factory);
        }

        @Bean
        CacheManager cacheManager(RedisConnectionFactory factory) {
            ObjectMapper mapper = new ObjectMapper();
            mapper.registerModule(new JavaTimeModule());
            mapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
            mapper.activateDefaultTyping(
                    LaissezFaireSubTypeValidator.instance,
                    ObjectMapper.DefaultTyping.NON_FINAL,
                    JsonTypeInfo.As.PROPERTY);

            GenericJackson2JsonRedisSerializer valueSerializer = new GenericJackson2JsonRedisSerializer(mapper);

            RedisCacheConfiguration defaults = RedisCacheConfiguration.defaultCacheConfig()
                    .serializeKeysWith(RedisSerializationContext.SerializationPair
                            .fromSerializer(new StringRedisSerializer()))
                    .serializeValuesWith(RedisSerializationContext.SerializationPair
                            .fromSerializer(valueSerializer))
                    .disableCachingNullValues()
                    .entryTtl(Duration.ofHours(1));

            Map<String, RedisCacheConfiguration> map = new HashMap<>();
            map.put(CacheNames.FEE_RULES, defaults.entryTtl(Duration.ofMinutes(30)));
            map.put(CacheNames.AGENT_RELATION, defaults.entryTtl(Duration.ofHours(1)));
            map.put(CacheNames.MERCHANT_PROFILE, defaults.entryTtl(Duration.ofHours(6)));
            map.put(CacheNames.MERCHANT_CONTRACT, defaults.entryTtl(Duration.ofHours(6)));

            return RedisCacheManager.builder(factory)
                    .cacheDefaults(defaults)
                    .withInitialCacheConfigurations(map)
                    .build();
        }
    }

    @Configuration
    @EnableCaching
    @ConditionalOnProperty(prefix = "payment.redis", name = "enabled", havingValue = "false")
    static class RedisDisabledConfig {

        @Bean
        CacheManager cacheManager() {
            return new ConcurrentMapCacheManager(
                    CacheNames.FEE_RULES,
                    CacheNames.AGENT_RELATION,
                    CacheNames.MERCHANT_PROFILE,
                    CacheNames.MERCHANT_CONTRACT);
        }
    }
}
