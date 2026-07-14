package com.payment.config;

import com.baomidou.dynamic.datasource.DynamicRoutingDataSource;
import com.payment.domain.datasource.DataSourceNames;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.annotation.Order;
import org.springframework.core.io.Resource;
import org.springframework.core.io.ResourceLoader;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.DatabaseMetaData;
import java.sql.ResultSet;

/**
 * 启动时检测并执行建表/种子数据脚本。
 * 动态数据源模式下分别初始化 config / data 数据源。
 * 分片模式下 data 层由 {@link ShardTableInitializer} 负责，此处只初始化 config。
 */
@Component
@Order(200)
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

    @Value("${pay.db.init.config-schema-locations:}")
    private String configSchemaLocation;

    @Value("${pay.shard.enabled:false}")
    private boolean shardEnabled;

    public DatabaseSchemaInitializer(DataSource dataSource, ResourceLoader resourceLoader) {
        this.dataSource = dataSource;
        this.resourceLoader = resourceLoader;
    }

    @Override
    public void afterPropertiesSet() throws Exception {
        if (dataSource instanceof DynamicRoutingDataSource dynamic) {
            initIfNeeded(dynamic.getDataSource(DataSourceNames.CONFIG), resolveConfigSchema(), dataLocation, "merchant_profile");
            if (shardEnabled) {
                log.info("分片模式已启用，跳过 data 数据源 schema 初始化（由 ShardTableInitializer 负责）");
                return;
            }
            initIfNeeded(dynamic.getDataSource(DataSourceNames.DATA), schemaLocation, null, CHECK_TABLE);
            return;
        }
        initIfNeeded(dataSource, schemaLocation, dataLocation, CHECK_TABLE);
    }

    private String resolveConfigSchema() {
        if (configSchemaLocation != null && !configSchemaLocation.isBlank()) {
            return configSchemaLocation;
        }
        return schemaLocation;
    }

    private void initIfNeeded(DataSource target, String schema, String data, String checkTable) throws Exception {
        if (target == null || !needsInitialization(target, checkTable)) {
            log.info("数据库表已存在，跳过 schema 初始化: checkTable={}", checkTable);
            return;
        }
        log.info("开始执行数据库初始化: schema={}", schema);
        ResourceDatabasePopulator populator = new ResourceDatabasePopulator();
        populator.setSeparator(";");
        populator.setContinueOnError(false);
        populator.setSqlScriptEncoding("UTF-8");
        populator.addScript(loadRequiredScript(schema));
        if (data != null && !data.isBlank()) {
            populator.addScript(loadRequiredScript(data));
        }
        populator.execute(target);
        log.info("数据库初始化完成");
    }

    private Resource loadRequiredScript(String location) {
        Resource resource = resourceLoader.getResource(location);
        if (!resource.exists()) {
            throw new IllegalStateException("SQL 脚本不存在: " + location);
        }
        return resource;
    }

    private boolean needsInitialization(DataSource target, String checkTable) throws Exception {
        try (Connection connection = target.getConnection()) {
            String catalog = connection.getCatalog();
            DatabaseMetaData metaData = connection.getMetaData();
            try (ResultSet rs = metaData.getTables(catalog, null, checkTable, new String[]{"TABLE"})) {
                return !rs.next();
            }
        }
    }
}
