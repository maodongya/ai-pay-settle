package com.payment.mq.config;

import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.ComponentScan;

@AutoConfiguration
@EnableConfigurationProperties(PayMqProperties.class)
@ComponentScan(basePackages = "com.payment.mq")
public class PayMqAutoConfiguration {
}
