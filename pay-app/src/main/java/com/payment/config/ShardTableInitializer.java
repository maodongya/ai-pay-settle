package com.payment.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import org.springframework.stereotype.Component;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.Statement;
import java.util.List;

/**
 * 分片模式下在各 pay_data_XX 库创建物理分表（trade_bill_0 ~ _3 等）。
 */
@Component
@ConditionalOnProperty(prefix = "pay.db.shard-init", name = "enabled", havingValue = "true")
public class ShardTableInitializer implements InitializingBean {

    private static final Logger log = LoggerFactory.getLogger(ShardTableInitializer.class);

    private static final List<String> SHARD_TABLES = List.of(
            "trade_bill", "clearance_task", "fee_calc_result", "split_detail",
            "account_voucher", "outbox_message", "merchant_settle_account", "account_flow",
            "settlement_order", "withdraw_apply", "merchant_payable_suspend", "reconcile_bill"
    );

    private final DataSource dataSource;

    @Value("${pay.shard.jdbc-url-template:jdbc:mysql://127.0.0.1:3306/pay_data_%02d?useUnicode=true&characterEncoding=utf8&serverTimezone=Asia/Shanghai&allowPublicKeyRetrieval=true&useSSL=false}")
    private String jdbcUrlTemplate;

    @Value("${pay.shard.jdbc-username:root}")
    private String jdbcUsername;

    @Value("${pay.shard.jdbc-password:123456}")
    private String jdbcPassword;

    public ShardTableInitializer(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    @Override
    public void afterPropertiesSet() throws Exception {
        ClassPathResource baseSchema = new ClassPathResource("db/schema-data.sql");
        if (!baseSchema.exists()) {
            baseSchema = new ClassPathResource("db/schema.sql");
        }
        for (int db = 0; db < 4; db++) {
            String jdbcUrl = String.format(jdbcUrlTemplate, db);
            try (Connection connection = createConnection(jdbcUrl);
                 Statement stmt = connection.createStatement()) {
                stmt.execute("CREATE DATABASE IF NOT EXISTS pay_data_" + String.format("%02d", db));
            }
            javax.sql.DataSource dbSource = singleDataSource(jdbcUrl);
            ensureBaseTables(dbSource, baseSchema);
            for (String table : SHARD_TABLES) {
                for (int suffix = 0; suffix < 4; suffix++) {
                    String physical = table + "_" + suffix;
                    executeIgnoreExists(dbSource,
                            "CREATE TABLE IF NOT EXISTS " + physical + " LIKE " + table);
                }
            }
            log.info("shard tables initialized for pay_data_{}", String.format("%02d", db));
        }
    }

    private void ensureBaseTables(javax.sql.DataSource dbSource, ClassPathResource baseSchema) {
        ResourceDatabasePopulator populator = new ResourceDatabasePopulator();
        populator.setSeparator(";");
        populator.addScript(baseSchema);
        populator.setContinueOnError(true);
        populator.execute(dbSource);
    }

    private void executeIgnoreExists(javax.sql.DataSource dbSource, String sql) throws Exception {
        try (Connection conn = dbSource.getConnection(); Statement stmt = conn.createStatement()) {
            stmt.execute(sql);
        }
    }

    private Connection createConnection(String jdbcUrl) throws Exception {
        return java.sql.DriverManager.getConnection(jdbcUrl, jdbcUsername, jdbcPassword);
    }

    private javax.sql.DataSource singleDataSource(String jdbcUrl) {
        com.zaxxer.hikari.HikariDataSource ds = new com.zaxxer.hikari.HikariDataSource();
        ds.setJdbcUrl(jdbcUrl);
        ds.setUsername(jdbcUsername);
        ds.setPassword(jdbcPassword);
        ds.setMaximumPoolSize(2);
        return ds;
    }
}
