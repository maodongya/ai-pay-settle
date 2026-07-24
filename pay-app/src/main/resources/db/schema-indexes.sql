-- 性能优化索引（MySQL 不支持 CREATE INDEX IF NOT EXISTS，重复执行时由初始化器忽略错误）
CREATE INDEX idx_clearance_status_shard ON clearance_task (status, shard_id);
CREATE INDEX idx_clearance_next_retry ON clearance_task (status, next_retry_time);
CREATE INDEX idx_outbox_status_time ON outbox_message (status, create_time);
CREATE INDEX idx_account_posting_status ON account_posting_outbox (status, create_time);
CREATE INDEX idx_account_flow_merchant_time ON account_flow (merchant_id, create_time);
CREATE INDEX idx_withdraw_settle_merchant ON withdraw_apply (settle_no, merchant_id);
CREATE INDEX idx_suspend_merchant_status ON merchant_payable_suspend (merchant_id, status, create_time);
CREATE INDEX idx_account_flow_settle_op ON account_flow (settle_no, op_type);
