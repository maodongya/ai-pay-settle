-- pay_data 数据层逻辑表模板（分片模式下复制为 table_0 ~ table_3）

CREATE TABLE IF NOT EXISTS trade_bill (
  id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '主键ID',
  bill_no VARCHAR(64) NOT NULL UNIQUE COMMENT '清算单据唯一号',
  bill_type TINYINT NOT NULL COMMENT '账单类型',
  business_line VARCHAR(32) NOT NULL COMMENT '业务线',
  category VARCHAR(32) NOT NULL COMMENT '品类',
  service_item VARCHAR(32) NOT NULL COMMENT '服务项目',
  merchant_id BIGINT NOT NULL COMMENT '商户ID',
  agent_id BIGINT COMMENT '一级代理ID',
  second_agent_id BIGINT COMMENT '二级代理ID',
  order_no VARCHAR(64) NOT NULL COMMENT '原始业务订单号',
  origin_bill_no VARCHAR(64) COMMENT '退款/冲正关联原单号',
  trade_amount DECIMAL(18,2) NOT NULL COMMENT '交易金额',
  city_code VARCHAR(16) NOT NULL COMMENT '城市编码',
  pay_channel VARCHAR(32) COMMENT '支付渠道',
  status TINYINT NOT NULL DEFAULT 0 COMMENT '账单状态',
  create_time TIMESTAMP NOT NULL COMMENT '创建时间',
  update_time TIMESTAMP NOT NULL COMMENT '更新时间'
) COMMENT='原始交易清算单据';

CREATE TABLE IF NOT EXISTS clearance_task (
  task_id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '任务ID',
  bill_no VARCHAR(64) NOT NULL UNIQUE COMMENT '关联账单号',
  merchant_id BIGINT NOT NULL COMMENT '商户ID',
  shard_id INT NOT NULL COMMENT '分片编号',
  status TINYINT NOT NULL DEFAULT 0 COMMENT '任务状态',
  retry_count INT NOT NULL DEFAULT 0 COMMENT '重试次数',
  error_msg VARCHAR(512) COMMENT '失败原因',
  next_retry_time TIMESTAMP COMMENT '下次业务重试时间',
  create_time TIMESTAMP NOT NULL COMMENT '创建时间',
  update_time TIMESTAMP NOT NULL COMMENT '更新时间'
) COMMENT='清算任务';

CREATE TABLE IF NOT EXISTS fee_calc_result (
  id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '主键ID',
  bill_no VARCHAR(64) NOT NULL UNIQUE COMMENT '账单号',
  merchant_id BIGINT NOT NULL COMMENT '商户ID',
  trade_amount DECIMAL(18,2) NOT NULL COMMENT '交易本金',
  platform_fee DECIMAL(18,2) NOT NULL COMMENT '平台手续费',
  agent_l1_share DECIMAL(18,2) NOT NULL COMMENT '一级代理分润',
  agent_l2_share DECIMAL(18,2) NOT NULL COMMENT '二级代理分润',
  partner_share DECIMAL(18,2) NOT NULL COMMENT '合伙人分成',
  merchant_income DECIMAL(18,2) NOT NULL COMMENT '商户实收',
  rule_snapshot TEXT COMMENT '命中规则快照(JSON)',
  calc_time TIMESTAMP NOT NULL COMMENT '计费时间'
) COMMENT='计费结果明细';

CREATE TABLE IF NOT EXISTS split_detail (
  id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '主键ID',
  bill_no VARCHAR(64) NOT NULL COMMENT '账单号',
  merchant_id BIGINT NOT NULL COMMENT '商户ID，分片键',
  party_type TINYINT NOT NULL COMMENT '参与方类型',
  party_id BIGINT NOT NULL COMMENT '参与方ID',
  amount DECIMAL(18,2) NOT NULL COMMENT '清分金额',
  direction TINYINT NOT NULL COMMENT '方向：1应收 2应付',
  create_time TIMESTAMP NOT NULL COMMENT '创建时间'
) COMMENT='清分明细';

CREATE TABLE IF NOT EXISTS outbox_message (
  id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '主键ID',
  biz_key VARCHAR(64) NOT NULL COMMENT '业务键',
  merchant_id BIGINT NOT NULL COMMENT '商户ID，分片键',
  topic VARCHAR(64) NOT NULL COMMENT '消息主题',
  payload TEXT NOT NULL COMMENT '消息载荷(JSON)',
  status TINYINT NOT NULL COMMENT '发送状态：0待发送 1已发送',
  create_time TIMESTAMP NOT NULL COMMENT '创建时间'
) COMMENT='可靠消息Outbox';

CREATE TABLE IF NOT EXISTS account_posting_outbox (
  id BIGINT AUTO_INCREMENT PRIMARY KEY,
  tenant_id VARCHAR(64) NOT NULL,
  merchant_id BIGINT NOT NULL,
  biz_no VARCHAR(128) NOT NULL,
  biz_type VARCHAR(32) NOT NULL,
  payload_json TEXT NOT NULL,
  status TINYINT NOT NULL DEFAULT 0 COMMENT '0 pending 1 success 2 failed',
  retry_count INT NOT NULL DEFAULT 0,
  last_error VARCHAR(512) NULL,
  transaction_no VARCHAR(64) NULL,
  create_time TIMESTAMP NOT NULL,
  update_time TIMESTAMP NOT NULL,
  UNIQUE KEY uk_posting (tenant_id, biz_no, biz_type)
) COMMENT='账务过账发件箱';

CREATE TABLE IF NOT EXISTS merchant_settle_account (
  account_id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '账户ID',
  merchant_id BIGINT NOT NULL UNIQUE COMMENT '商户ID',
  wait_balance DECIMAL(18,2) NOT NULL DEFAULT 0 COMMENT '待结算可用余额',
  frozen_balance DECIMAL(18,2) NOT NULL DEFAULT 0 COMMENT '提现冻结金额',
  settle_card_no VARCHAR(128) COMMENT '结算卡号(AES加密)',
  settle_mode TINYINT NOT NULL COMMENT '结算模式',
  version INT NOT NULL DEFAULT 0 COMMENT '乐观锁版本号',
  create_time TIMESTAMP NOT NULL COMMENT '创建时间',
  update_time TIMESTAMP NOT NULL COMMENT '更新时间'
) COMMENT='商户中间待结算账户';

CREATE TABLE IF NOT EXISTS account_flow (
  flow_id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '流水ID',
  merchant_id BIGINT NOT NULL COMMENT '商户ID',
  bill_no VARCHAR(64) COMMENT '关联账单号',
  settle_no VARCHAR(64) COMMENT '关联结算单号',
  op_type TINYINT NOT NULL COMMENT '操作类型',
  amount DECIMAL(18,2) NOT NULL COMMENT '变动金额',
  before_balance DECIMAL(18,2) NOT NULL COMMENT '变动前余额',
  after_balance DECIMAL(18,2) NOT NULL COMMENT '变动后余额',
  create_time TIMESTAMP NOT NULL COMMENT '创建时间',
  CONSTRAINT uk_biz UNIQUE (bill_no, op_type)
) COMMENT='账户流水';

CREATE TABLE IF NOT EXISTS settlement_order (
  id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '主键ID',
  settle_no VARCHAR(64) NOT NULL UNIQUE COMMENT '结算单号',
  merchant_id BIGINT NOT NULL COMMENT '商户ID',
  settle_amount DECIMAL(18,2) NOT NULL COMMENT '结算金额',
  settle_mode TINYINT NOT NULL COMMENT '结算模式',
  settle_card_no VARCHAR(128) NOT NULL COMMENT '结算卡号',
  status TINYINT NOT NULL COMMENT '结算状态',
  channel_trade_no VARCHAR(64) COMMENT '银行渠道流水号',
  fail_reason VARCHAR(256) COMMENT '失败原因',
  origin_settle_no VARCHAR(64) COMMENT '原结算单号(重试/冲正)',
  create_time TIMESTAMP NOT NULL COMMENT '创建时间',
  update_time TIMESTAMP NOT NULL COMMENT '更新时间'
) COMMENT='结算单';

CREATE TABLE IF NOT EXISTS withdraw_apply (
  apply_id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '申请ID',
  apply_no VARCHAR(64) NOT NULL UNIQUE COMMENT '提现申请单号',
  merchant_id BIGINT NOT NULL COMMENT '商户ID',
  amount DECIMAL(18,2) NOT NULL COMMENT '提现金额',
  settle_no VARCHAR(64) COMMENT '关联结算单号',
  status TINYINT NOT NULL COMMENT '申请状态',
  create_time TIMESTAMP NOT NULL COMMENT '创建时间'
) COMMENT='提现申请';

CREATE TABLE IF NOT EXISTS merchant_payable_suspend (
  id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '主键ID',
  merchant_id BIGINT NOT NULL COMMENT '商户ID',
  bill_no VARCHAR(64) NOT NULL UNIQUE COMMENT '关联账单号',
  suspend_amount DECIMAL(18,2) NOT NULL COMMENT '挂账金额',
  settled_amount DECIMAL(18,2) NOT NULL DEFAULT 0 COMMENT '已结算金额',
  status TINYINT NOT NULL COMMENT '挂账状态：0挂账中 1已结清',
  create_time TIMESTAMP NOT NULL COMMENT '创建时间'
) COMMENT='商户应付挂账';

CREATE TABLE IF NOT EXISTS account_voucher (
  voucher_id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '凭证ID',
  bill_no VARCHAR(64) NOT NULL COMMENT '关联账单号',
  merchant_id BIGINT NOT NULL COMMENT '商户ID，分片键',
  debit_subject VARCHAR(32) NOT NULL COMMENT '借方科目',
  credit_subject VARCHAR(32) NOT NULL COMMENT '贷方科目',
  amount DECIMAL(18,2) NOT NULL COMMENT '凭证金额',
  sync_status TINYINT NOT NULL DEFAULT 0 COMMENT 'ERP同步状态',
  create_time TIMESTAMP NOT NULL COMMENT '创建时间'
) COMMENT='会计凭证';

CREATE TABLE IF NOT EXISTS reconcile_bill (
  bill_id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '对账单ID',
  merchant_id BIGINT NOT NULL COMMENT '商户ID',
  bill_date DATE NOT NULL COMMENT '对账日期',
  total_income DECIMAL(18,2) NOT NULL DEFAULT 0 COMMENT '当日总收入',
  total_settle DECIMAL(18,2) NOT NULL DEFAULT 0 COMMENT '当日总结算',
  file_url VARCHAR(256) COMMENT '对账文件地址',
  create_time TIMESTAMP NOT NULL COMMENT '创建时间',
  CONSTRAINT uk_merchant_date UNIQUE (merchant_id, bill_date)
) COMMENT='商户对账单';

-- 索引（MySQL 8 无 IF NOT EXISTS，重复执行忽略错误）
CREATE INDEX idx_clearance_status_shard ON clearance_task (status, shard_id);
CREATE INDEX idx_clearance_next_retry ON clearance_task (status, next_retry_time);
CREATE INDEX idx_outbox_status_time ON outbox_message (status, create_time);
CREATE INDEX idx_account_posting_status ON account_posting_outbox (status, create_time);
CREATE INDEX idx_account_flow_merchant_time ON account_flow (merchant_id, create_time);
CREATE INDEX idx_withdraw_settle_merchant ON withdraw_apply (settle_no, merchant_id);
CREATE INDEX idx_suspend_merchant_status ON merchant_payable_suspend (merchant_id, status, create_time);
CREATE INDEX idx_account_flow_settle_op ON account_flow (settle_no, op_type);
