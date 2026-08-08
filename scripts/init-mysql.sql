-- 创建 pay_settle 数据库（字符集 utf8mb4）
CREATE DATABASE IF NOT EXISTS pay_settle
  DEFAULT CHARACTER SET utf8mb4
  COLLATE utf8mb4_unicode_ci;

USE pay_settle;

-- MQ 性能优化：清算任务退避字段（已有库增量迁移）
ALTER TABLE clearance_task ADD COLUMN next_retry_time TIMESTAMP NULL COMMENT '下次业务重试时间' AFTER error_msg;

-- 异常工单表
CREATE TABLE IF NOT EXISTS exception_record (
  exception_id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '工单ID',
  exception_no VARCHAR(32) NOT NULL UNIQUE COMMENT '工单号',
  exception_code VARCHAR(16) NOT NULL COMMENT '异常编码',
  severity TINYINT NOT NULL COMMENT '严重级别',
  biz_domain VARCHAR(16) NOT NULL COMMENT '业务域',
  biz_key VARCHAR(64) NOT NULL COMMENT '业务键',
  title VARCHAR(128) NOT NULL COMMENT '标题',
  detail VARCHAR(1024) COMMENT '详情',
  status TINYINT NOT NULL DEFAULT 0 COMMENT '0待处理 1处理中 2已解决 3已忽略',
  create_time TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间'
) COMMENT='异常工单';

-- 分片路由表（已有库增量迁移）
CREATE TABLE IF NOT EXISTS bill_route (
  bill_no VARCHAR(64) NOT NULL PRIMARY KEY COMMENT '清算单据号',
  merchant_id BIGINT NOT NULL COMMENT '商户ID，分片键',
  bill_type TINYINT NOT NULL COMMENT '单据类型',
  create_time TIMESTAMP NOT NULL COMMENT '创建时间'
) COMMENT='单据分片路由索引';

CREATE TABLE IF NOT EXISTS settle_route (
  settle_no VARCHAR(64) NOT NULL PRIMARY KEY COMMENT '结算单号',
  merchant_id BIGINT NOT NULL COMMENT '商户ID，分片键',
  create_time TIMESTAMP NOT NULL COMMENT '创建时间'
) COMMENT='结算单分片路由索引';

INSERT IGNORE INTO bill_route (bill_no, merchant_id, bill_type, create_time)
SELECT bill_no, merchant_id, bill_type, create_time FROM trade_bill;

-- Outbox 分片键（已有库增量迁移）
ALTER TABLE outbox_message ADD COLUMN merchant_id BIGINT NULL COMMENT '商户ID，分片键' AFTER biz_key;
UPDATE outbox_message o
  INNER JOIN trade_bill t ON o.biz_key = t.bill_no
  SET o.merchant_id = t.merchant_id
  WHERE o.merchant_id IS NULL;
ALTER TABLE outbox_message MODIFY COLUMN merchant_id BIGINT NOT NULL COMMENT '商户ID，分片键';

-- split_detail / account_voucher 分片键（已有库增量迁移）
ALTER TABLE split_detail ADD COLUMN merchant_id BIGINT NULL COMMENT '商户ID，分片键' AFTER bill_no;
UPDATE split_detail s
  INNER JOIN trade_bill t ON s.bill_no = t.bill_no
  SET s.merchant_id = t.merchant_id
  WHERE s.merchant_id IS NULL;
ALTER TABLE split_detail MODIFY COLUMN merchant_id BIGINT NOT NULL COMMENT '商户ID，分片键';

ALTER TABLE account_voucher ADD COLUMN merchant_id BIGINT NULL COMMENT '商户ID，分片键' AFTER bill_no;
UPDATE account_voucher v
  INNER JOIN trade_bill t ON v.bill_no = t.bill_no
  SET v.merchant_id = t.merchant_id
  WHERE v.merchant_id IS NULL;
ALTER TABLE account_voucher MODIFY COLUMN merchant_id BIGINT NOT NULL COMMENT '商户ID，分片键';

-- MQ 性能优化补充索引（已有库可重复执行，重复报错可忽略）
ALTER TABLE clearance_task ADD INDEX idx_status_shard (status, shard_id);
ALTER TABLE clearance_task ADD INDEX idx_next_retry (status, next_retry_time);
ALTER TABLE outbox_message ADD INDEX idx_status_time (status, create_time);
ALTER TABLE account_flow ADD INDEX idx_merchant_time (merchant_id, create_time);
