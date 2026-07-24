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
    private static final String ACCOUNT_POSTING_OUTBOX_TABLE = "account_posting_outbox";
    private static final String ACCOUNT_POSTING_OUTBOX_DDL =
            "CREATE TABLE IF NOT EXISTS account_posting_outbox ("
                    + "id BIGINT AUTO_INCREMENT PRIMARY KEY,"
                    + "tenant_id VARCHAR(64) NOT NULL,"
                    + "merchant_id BIGINT NOT NULL,"
                    + "biz_no VARCHAR(128) NOT NULL,"
                    + "biz_type VARCHAR(32) NOT NULL,"
                    + "payload_json TEXT NOT NULL,"
                    + "status TINYINT NOT NULL DEFAULT 0 COMMENT '0 pending 1 success 2 failed',"
                    + "retry_count INT NOT NULL DEFAULT 0,"
                    + "last_error VARCHAR(512) NULL,"
                    + "transaction_no VARCHAR(64) NULL,"
                    + "create_time TIMESTAMP NOT NULL,"
                    + "update_time TIMESTAMP NOT NULL,"
                    + "UNIQUE KEY uk_posting (tenant_id, biz_no, biz_type)"
                    + ") COMMENT='账务过账发件箱'";

    private final DataSource dataSource;
    private final ResourceLoader resourceLoader;

    @Value("${spring.sql.init.schema-locations:classpath:db/schema.sql}")
    private String schemaLocation;

    @Value("${spring.sql.init.data-locations:classpath:db/data-mysql.sql}")
    private String dataLocation;

    @Value("${pay.db.init.index-locations:classpath:db/schema-indexes.sql}")
    private String indexLocation;

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
            initIfNeeded(dynamic.getDataSource(DataSourceNames.CONFIG), resolveConfigSchema(), null, "merchant_profile");
            seedData(dynamic.getDataSource(DataSourceNames.CONFIG));
            if (shardEnabled) {
                log.info("分片模式已启用，跳过 data 数据源 schema 初始化（由 ShardTableInitializer 负责）");
                return;
            }
            initIfNeeded(dynamic.getDataSource(DataSourceNames.DATA), schemaLocation, null, CHECK_TABLE);
            ensureAccountPostingOutbox(dynamic.getDataSource(DataSourceNames.DATA));
            applyIndexes(dynamic.getDataSource(DataSourceNames.DATA));
            return;
        }
        initIfNeeded(dataSource, schemaLocation, null, CHECK_TABLE);
        ensureAccountPostingOutbox(dataSource);
        applyIndexes(dataSource);
        seedData(dataSource);
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
        log.info("数据库 schema 初始化完成");
    }

    private void seedData(DataSource target) throws Exception {
        if (target == null || dataLocation == null || dataLocation.isBlank()) {
            return;
        }
        log.info("执行种子数据脚本: data={}", dataLocation);
        ResourceDatabasePopulator populator = new ResourceDatabasePopulator();
        populator.setSeparator(";");
        populator.setContinueOnError(true);
        populator.setSqlScriptEncoding("UTF-8");
        populator.addScript(loadRequiredScript(dataLocation));
        populator.execute(target);
        log.info("种子数据脚本执行完成");
    }

    private void applyIndexes(DataSource target) throws Exception {
        if (target == null || indexLocation == null || indexLocation.isBlank()) {
            return;
        }
        Resource resource = resourceLoader.getResource(indexLocation);
        if (!resource.exists()) {
            return;
        }
        log.info("执行索引脚本: indexes={}", indexLocation);
        ResourceDatabasePopulator populator = new ResourceDatabasePopulator();
        populator.setSeparator(";");
        populator.setContinueOnError(true);
        populator.setSqlScriptEncoding("UTF-8");
        populator.addScript(resource);
        populator.execute(target);
    }

    private Resource loadRequiredScript(String location) {
        Resource resource = resourceLoader.getResource(location);
        if (!resource.exists()) {
            throw new IllegalStateException("SQL 脚本不存在: " + location);
        }
        return resource;
    }

    private void ensureAccountPostingOutbox(DataSource target) throws Exception {
        if (target == null || !tableExists(target, CHECK_TABLE) || tableExists(target, ACCOUNT_POSTING_OUTBOX_TABLE)) {
            return;
        }
        log.info("检测到缺少 {} 表，执行增量建表", ACCOUNT_POSTING_OUTBOX_TABLE);
        ResourceDatabasePopulator populator = new ResourceDatabasePopulator();
        populator.setSeparator(";");
        populator.addScript(new org.springframework.core.io.ByteArrayResource(
                (ACCOUNT_POSTING_OUTBOX_DDL + ";").getBytes(java.nio.charset.StandardCharsets.UTF_8)));
        populator.execute(target);
    }

    private boolean tableExists(DataSource target, String tableName) throws Exception {
        try (Connection connection = target.getConnection()) {
            String catalog = connection.getCatalog();
            DatabaseMetaData metaData = connection.getMetaData();
            try (ResultSet rs = metaData.getTables(catalog, null, tableName, new String[]{"TABLE"})) {
                return rs.next();
            }
        }
    }

    private boolean needsInitialization(DataSource target, String checkTable) throws Exception {
        return !tableExists(target, checkTable);
    }
}
