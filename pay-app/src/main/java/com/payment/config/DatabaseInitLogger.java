package com.payment.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * 启动完成后输出数据库初始化状态。
 */
@Component
public class DatabaseInitLogger {

    private static final Logger log = LoggerFactory.getLogger(DatabaseInitLogger.class);

    @Value("${pay.db.init.enabled:true}")
    private boolean initEnabled;

    @Value("${spring.datasource.url:}")
    private String datasourceUrl;

    @Value("${spring.sql.init.data-locations:classpath:db/data-mysql.sql}")
    private String dataScriptLocation;

    @EventListener(ApplicationReadyEvent.class)
    public void onReady() {
        if (initEnabled) {
            log.info("数据库已自动初始化: schema=classpath:db/schema.sql, data={}, url={}",
                    dataScriptLocation, maskPassword(datasourceUrl));
        } else {
            log.info("数据库 SQL 初始化已关闭 (pay.db.init.enabled=false), url={}", maskPassword(datasourceUrl));
        }
    }

    private static String maskPassword(String url) {
        if (url == null || url.isBlank()) {
            return url;
        }
        return url.replaceAll("password=[^&]*", "password=***");
    }
}
