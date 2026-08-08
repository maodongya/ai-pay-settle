package com.payment.control.config;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@EnableConfigurationProperties(DbMonitorProperties.class)
public class PayControlAutoConfiguration {
}
