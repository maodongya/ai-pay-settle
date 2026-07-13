-- 分库分表环境初始化：pay_config + pay_data_00~03
-- 用法：mysql -uroot -p < scripts/init-sharding.sql

CREATE DATABASE IF NOT EXISTS pay_config
  DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

CREATE DATABASE IF NOT EXISTS pay_data_00
  DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
CREATE DATABASE IF NOT EXISTS pay_data_01
  DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
CREATE DATABASE IF NOT EXISTS pay_data_02
  DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;
CREATE DATABASE IF NOT EXISTS pay_data_03
  DEFAULT CHARACTER SET utf8mb4 COLLATE utf8mb4_unicode_ci;

-- config 库表由应用启动时 schema-config.sql 初始化
-- data 物理分表由 ShardTableInitializer 在 sharding profile 下自动创建
