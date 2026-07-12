package com.payment.mq.config; // MQ 自动配置包

import org.springframework.boot.autoconfigure.AutoConfiguration; // Spring Boot 自动配置
import org.springframework.boot.context.properties.EnableConfigurationProperties; // 启用配置属性
import org.springframework.context.annotation.ComponentScan; // 扫描 pay-mq 包

/**
 * pay-mq 模块自动配置：注册 Producer、Consumer 辅助 Bean 与配置属性。
 */
@AutoConfiguration // 自动配置入口
@EnableConfigurationProperties({PayMqProperties.class, MqConsumerProperties.class, MqBacklogProperties.class}) // 绑定 MQ 相关 yml
@ComponentScan(basePackages = "com.payment.mq") // 扫描本模块组件
public class PayMqAutoConfiguration {
    // 无额外 @Bean：Producer / Classifier / Invoker 等由 @Component 注册
}
