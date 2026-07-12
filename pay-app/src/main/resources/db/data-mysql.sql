-- 初始化种子数据（幂等：重复执行不会报错）
-- 使用 INSERT IGNORE，适用于 MySQL；H2 MySQL 兼容模式同样支持

INSERT IGNORE INTO merchant_profile (merchant_id, merchant_name, status) VALUES (100001, 'Demo Merchant', 1);

INSERT IGNORE INTO merchant_contract (merchant_id, sign_date, settle_mode, min_withdraw, status)
VALUES (100001, '2025-09-15', 2, 100.00, 1);

INSERT IGNORE INTO merchant_settle_account (merchant_id, wait_balance, frozen_balance, settle_card_no, settle_mode, version, create_time, update_time)
VALUES (100001, 0.00, 0.00, '6222000012345678', 2, 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);

INSERT IGNORE INTO agent_merchant_relation (agent_id, second_agent_id, merchant_id, split_party_id, valid_start, valid_end)
VALUES (200001, 200002, 100001, 300001, '2026-01-01 00:00:00', NULL);

-- Platform fee 0.6%
INSERT IGNORE INTO fee_share_rule (rule_name, target_type, business_line, category, service_item, city_code, share_mode, first_month_value, step_down_val, min_share, valid_start, status, create_time)
VALUES ('Platform default', 1, '*', '*', '*', '*', 1, 0.0060, 0, 0, '2026-01-01 00:00:00', 1, CURRENT_TIMESTAMP);

-- Agent L1 5%
INSERT IGNORE INTO fee_share_rule (rule_name, target_type, business_line, category, service_item, city_code, share_mode, first_month_value, step_down_val, min_share, valid_start, status, create_time)
VALUES ('Agent L1 default', 2, '*', '*', '*', '*', 1, 0.0500, 0, 0, '2026-01-01 00:00:00', 1, CURRENT_TIMESTAMP);

-- Agent L2 3%
INSERT IGNORE INTO fee_share_rule (rule_name, target_type, business_line, category, service_item, city_code, share_mode, first_month_value, step_down_val, min_share, valid_start, status, create_time)
VALUES ('Agent L2 default', 3, '*', '*', '*', '*', 1, 0.0300, 0, 0, '2026-01-01 00:00:00', 1, CURRENT_TIMESTAMP);

-- Partner step down: 2% first month, -0.1%/month, min 0.5%
INSERT IGNORE INTO fee_share_rule (rule_name, target_type, business_line, category, service_item, city_code, share_mode, first_month_value, step_down_val, min_share, valid_start, status, create_time)
VALUES ('Partner step down', 4, '*', '*', '*', '*', 3, 0.0200, 0.0010, 0.0050, '2026-01-01 00:00:00', 1, CURRENT_TIMESTAMP);

-- T1 merchant for batch settlement
INSERT IGNORE INTO merchant_profile (merchant_id, merchant_name, status) VALUES (100002, 'T1 Merchant', 1);

INSERT IGNORE INTO merchant_contract (merchant_id, sign_date, settle_mode, min_withdraw, status)
VALUES (100002, '2026-01-15', 1, 100.00, 1);

INSERT IGNORE INTO merchant_settle_account (merchant_id, wait_balance, frozen_balance, settle_card_no, settle_mode, version, create_time, update_time)
VALUES (100002, 0.00, 0.00, '6222000098765432', 1, 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);

INSERT IGNORE INTO agent_merchant_relation (agent_id, second_agent_id, merchant_id, split_party_id, valid_start, valid_end)
VALUES (200001, NULL, 100002, NULL, '2026-01-01 00:00:00', NULL);
