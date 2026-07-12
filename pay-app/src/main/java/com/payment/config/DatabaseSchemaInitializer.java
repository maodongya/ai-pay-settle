package com.payment.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.ResultSet;

/**
 * 启动时检测并执行建表/种子数据脚本（MySQL 等非内嵌库兜底）。
 */
@Component
@ConditionalOnProperty(prefix = "pay.db.init", name = "enabled", havingValue = "true", matchIfMissing = true)
public class DatabaseSchemaInitializer implements InitializingBean {

    private static final Logger log = LoggerFactory.getLogger(DatabaseSchemaInitializer.class);
    private static final String CHECK_TABLE = "trade_bill";

    private final DataSource dataSource;
    private final ResourceLoader resourceLoader;

    @Value("${spring.sql.init.schema-locations:classpath:db/schema.sql}")
    private String schemaLocation;

    @Value("${spring.sql.init.data-locations:classpath:db/data-mysql.sql}")
    private String dataLocation;

    public DatabaseSchemaInitializer(DataSource dataSource, ResourceLoader resourceLoader) {
        this.dataSource = dataSource;
        this.resourceLoader = resourceLoader;
    }

    @Override
    public void afterPropertiesSet() throws Exception {
        if (!needsInitialization()) {
            log.info("数据库表已存在，跳过 schema/data 初始化");
            return;
        }
        log.info("检测到数据库未初始化，开始执行脚本: schema={}, data={}", schemaLocation, dataLocation);
        ResourceDatabasePopulator populator = new ResourceDatabasePopulator();
        populator.setSeparator(";");
        populator.setContinueOnError(false);
        populator.setSqlScriptEncoding("UTF-8");
        populator.addScript(loadRequiredScript(schemaLocation));
        populator.addScript(loadRequiredScript(dataLocation));
        populator.execute(dataSource);
        log.info("数据库初始化完成");
    }

    private Resource loadRequiredScript(String location) {
        Resource resource = resourceLoader.getResource(location);
        if (!resource.exists()) {
            throw new IllegalStateException("SQL 脚本不存在: " + location);
        }
        return resource;
    }

    private boolean needsInitialization() throws Exception {
        try (Connection connection = dataSource.getConnection()) {
            String catalog = connection.getCatalog();
            DatabaseMetaData metaData = connection.getMetaData();
            try (ResultSet rs = metaData.getTables(catalog, null, CHECK_TABLE, new String[]{"TABLE"})) {
                return !rs.next();
            }
        }
    }
}
