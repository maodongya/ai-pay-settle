-- 初始化种子数据（幂等：重复执行不会报错）
-- 使用 INSERT IGNORE，适用于 MySQL；H2 MySQL 兼容模式同样支持

INSERT IGNORE INTO merchant_profile (merchant_id, merchant_name, status) VALUES (100001, 'Demo Merchant', 1);

INSERT IGNORE INTO merchant_contract (merchant_id, sign_date, settle_mode, min_withdraw, status)
VALUES (100001, '2025-09-15', 2, 100.00, 1);

INSERT IGNORE INTO merchant_settle_account (merchant_id, wait_balance, frozen_balance, settle_card_no, settle_mode, version, create_time, update_time)
VALUES (100001, 905.89, 100.00, '6222000012345678', 2, 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);

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

-- 控制台演示：交易账单 / 清算任务 / 结算单 / 账户流水
INSERT IGNORE INTO trade_bill (bill_no, bill_type, business_line, category, service_item, merchant_id, agent_id, second_agent_id, order_no, trade_amount, city_code, pay_channel, status, create_time, update_time)
VALUES
('CLDEMO001', 1, 'A', 'A01', 'A0101', 100001, 200001, 200002, 'ODDEMO001', 1000.00, '110000', 'ALI_PAY', 2, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
('CLDEMO002', 1, 'A', 'A01', 'A0101', 100001, 200001, 200002, 'ODDEMO002', 500.00, '110000', 'WECHAT', 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
('CLDEMO003', 1, 'A', 'A01', 'A0101', 100002, 200001, NULL, 'ODDEMO003', 800.00, '310000', 'ALI_PAY', 1, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
('CLDEMO004', 1, 'A', 'A01', 'A0101', 100001, 200001, 200002, 'ODDEMO004', 300.00, '110000', 'ALI_PAY', 3, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);

INSERT IGNORE INTO clearance_task (bill_no, merchant_id, shard_id, status, retry_count, error_msg, create_time, update_time)
VALUES
('CLDEMO001', 100001, 1, 2, 0, NULL, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
('CLDEMO002', 100001, 1, 0, 0, NULL, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
('CLDEMO003', 100002, 2, 1, 0, NULL, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
('CLDEMO004', 100001, 1, 3, 2, 'fee calc timeout', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);

INSERT IGNORE INTO fee_calc_result (bill_no, merchant_id, trade_amount, platform_fee, agent_l1_share, agent_l2_share, partner_share, merchant_income, rule_snapshot, calc_time)
VALUES ('CLDEMO001', 100001, 1000.00, 6.00, 50.00, 30.00, 8.11, 905.89, '{"demo":true}', CURRENT_TIMESTAMP);

INSERT IGNORE INTO settlement_order (settle_no, merchant_id, settle_amount, settle_mode, settle_card_no, status, channel_trade_no, fail_reason, create_time, update_time)
VALUES
('ST20260718001', 100001, 500.00, 2, '6222000012345678', 3, 'CH20260718001', NULL, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
('ST20260718002', 100001, 200.00, 2, '6222000012345678', 2, NULL, NULL, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP),
('ST20260718003', 100002, 100.00, 1, '6222000098765432', 4, NULL, 'channel timeout', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);

INSERT IGNORE INTO account_flow (merchant_id, bill_no, settle_no, op_type, amount, before_balance, after_balance, create_time)
VALUES
(100001, 'CLDEMO001', NULL, 1, 905.89, 0.00, 905.89, CURRENT_TIMESTAMP),
(100001, NULL, 'ST20260718001', 2, 500.00, 905.89, 405.89, CURRENT_TIMESTAMP);

INSERT IGNORE INTO reconcile_bill (merchant_id, bill_date, total_income, total_settle, file_url, create_time)
VALUES (100001, '2026-07-17', 905.89, 500.00, '/reconcile/100001/2026-07-17.csv', CURRENT_TIMESTAMP);
