package com.payment; // 应用测试包

import org.junit.jupiter.api.Test; // 测试方法注解
import org.springframework.boot.test.context.SpringBootTest; // Spring Boot 集成测试

/**
 * 应用启动上下文加载测试。
 */
@SpringBootTest // Spring Boot 集成测试
class PaySettleApplicationTest {

    /**
     * 验证 Spring 应用上下文能正常加载。
     */
    @Test // 测试方法
    void contextLoads() {
    }
}
