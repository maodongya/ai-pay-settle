package com.payment;

import com.baomidou.mybatisplus.annotation.DbType;
import com.baomidou.mybatisplus.extension.plugins.MybatisPlusInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.OptimisticLockerInnerInterceptor;
import com.baomidou.mybatisplus.extension.plugins.inner.PaginationInnerInterceptor;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.payment.common.config.PayAccountProperties;
import org.apache.dubbo.config.spring.context.annotation.EnableDubbo;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * 支付结算系统 Spring Boot 启动类。
 */
@SpringBootApplication(scanBasePackages = "com.payment")
@MapperScan("com.payment.domain.mapper")
@EnableScheduling
@EnableAsync
@EnableDubbo
@EnableConfigurationProperties(PayAccountProperties.class)
public class PaySettleApplication {

    public static void main(String[] args) {
        SpringApplication.run(PaySettleApplication.class, args);
    }

    /**
     * 全局 JSON 配置：忽略未知字段（兼容 MQ 消息 version/payTime 等扩展字段）。
     */
    @Bean
    ObjectMapper objectMapper() {
        ObjectMapper mapper = new ObjectMapper(); // 创建 Jackson 映射器
        mapper.registerModule(new JavaTimeModule()); // 支持 Java 8 时间类型
        mapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS); // 日期输出 ISO-8601
        mapper.disable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES); // 忽略 DTO 未声明字段
        return mapper; // 供 Consumer / Service 注入
    }

    @Bean
    MybatisPlusInterceptor mybatisPlusInterceptor() {
        MybatisPlusInterceptor interceptor = new MybatisPlusInterceptor();
        interceptor.addInnerInterceptor(new OptimisticLockerInnerInterceptor());
        interceptor.addInnerInterceptor(new PaginationInnerInterceptor(DbType.MYSQL));
        return interceptor;
    }
}
