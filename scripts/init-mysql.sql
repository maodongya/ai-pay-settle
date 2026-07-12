-- 创建 pay_settle 数据库（字符集 utf8mb4）
CREATE DATABASE IF NOT EXISTS pay_settle
  DEFAULT CHARACTER SET utf8mb4
  COLLATE utf8mb4_unicode_ci;

USE pay_settle;

-- MQ 性能优化补充索引（已有库可重复执行）
ALTER TABLE clearance_task ADD INDEX idx_status_shard (status, shard_id);
ALTER TABLE clearance_task ADD INDEX idx_next_retry (status, next_retry_time);
ALTER TABLE outbox_message ADD INDEX idx_status_time (status, create_time);
ALTER TABLE account_flow ADD INDEX idx_merchant_time (merchant_id, create_time);
