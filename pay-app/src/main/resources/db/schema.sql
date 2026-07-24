-- 原始交易清算单据
CREATE TABLE IF NOT EXISTS trade_bill (
  id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '主键ID',
  bill_no VARCHAR(64) NOT NULL UNIQUE COMMENT '清算单据唯一号',
  bill_type TINYINT NOT NULL COMMENT '账单类型：1收款 2退款 3充值 4分润 5奖惩 6授权 7出款 8订单清分',
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
  status TINYINT NOT NULL DEFAULT 0 COMMENT '账单状态：0待清算 1清算中 2已清算 3失败 4等待原单',
  create_time TIMESTAMP NOT NULL COMMENT '创建时间',
  update_time TIMESTAMP NOT NULL COMMENT '更新时间'
) COMMENT='原始交易清算单据';

-- 清算任务
CREATE TABLE IF NOT EXISTS clearance_task (
  task_id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '任务ID',
  bill_no VARCHAR(64) NOT NULL UNIQUE COMMENT '关联账单号',
  merchant_id BIGINT NOT NULL COMMENT '商户ID',
  shard_id INT NOT NULL COMMENT '分片编号',
  status TINYINT NOT NULL DEFAULT 0 COMMENT '任务状态：0待执行 1执行中 2成功 3失败 4死信',
  retry_count INT NOT NULL DEFAULT 0 COMMENT '重试次数',
  error_msg VARCHAR(512) COMMENT '失败原因',
  next_retry_time TIMESTAMP COMMENT '下次业务重试时间',
  create_time TIMESTAMP NOT NULL COMMENT '创建时间',
  update_time TIMESTAMP NOT NULL COMMENT '更新时间'
) COMMENT='清算任务';

-- 计费分润规则
CREATE TABLE IF NOT EXISTS fee_share_rule (
  rule_id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '规则ID',
  rule_name VARCHAR(128) NOT NULL COMMENT '规则名称',
  target_type TINYINT NOT NULL COMMENT '目标类型：1平台 2一级代理 3二级代理 4合伙人',
  business_line VARCHAR(32) NOT NULL COMMENT '业务线，*表示通配',
  category VARCHAR(32) NOT NULL COMMENT '品类，*表示通配',
  service_item VARCHAR(32) NOT NULL COMMENT '服务项目，*表示通配',
  city_code VARCHAR(16) NOT NULL COMMENT '城市编码，*表示通配',
  share_mode TINYINT NOT NULL COMMENT '分润模式：1固定比例 2固定金额 3阶梯递减',
  first_month_value DECIMAL(18,4) NOT NULL COMMENT '首月/基准分润值',
  step_down_val DECIMAL(18,4) COMMENT '阶梯递减步长',
  min_share DECIMAL(18,4) NOT NULL COMMENT '最低分润值',
  valid_start TIMESTAMP NOT NULL COMMENT '生效开始时间',
  valid_end TIMESTAMP COMMENT '生效结束时间',
  status TINYINT NOT NULL COMMENT '规则状态：0待审 1生效 2失效 3驳回',
  create_time TIMESTAMP NOT NULL COMMENT '创建时间'
) COMMENT='计费分润规则';

-- 代理-商户-分润关系
CREATE TABLE IF NOT EXISTS agent_merchant_relation (
  rel_id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '关系ID',
  agent_id BIGINT NOT NULL COMMENT '一级代理ID',
  second_agent_id BIGINT COMMENT '二级代理ID',
  merchant_id BIGINT NOT NULL COMMENT '商户ID',
  split_party_id BIGINT COMMENT '合伙人/分账方ID',
  valid_start TIMESTAMP NOT NULL COMMENT '关系生效时间',
  valid_end TIMESTAMP COMMENT '关系失效时间'
) COMMENT='代理-商户-分润关系';

-- 商户档案
CREATE TABLE IF NOT EXISTS merchant_profile (
  merchant_id BIGINT PRIMARY KEY COMMENT '商户ID',
  merchant_name VARCHAR(128) NOT NULL COMMENT '商户名称',
  status TINYINT NOT NULL COMMENT '商户状态：0冻结 1正常'
) COMMENT='商户档案';

-- 商户签约合同
CREATE TABLE IF NOT EXISTS merchant_contract (
  contract_id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '合同ID',
  merchant_id BIGINT NOT NULL UNIQUE COMMENT '商户ID',
  sign_date DATE NOT NULL COMMENT '签约日期',
  settle_mode TINYINT NOT NULL COMMENT '结算模式：1T1 2D0 3D1 4H0 5S0',
  min_withdraw DECIMAL(18,2) NOT NULL COMMENT '最低提现金额',
  status TINYINT NOT NULL COMMENT '合同状态：0失效 1有效'
) COMMENT='商户签约合同';

-- 计费结果明细
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

-- 清分明细
CREATE TABLE IF NOT EXISTS split_detail (
  id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '主键ID',
  bill_no VARCHAR(64) NOT NULL COMMENT '账单号',
  merchant_id BIGINT NOT NULL COMMENT '商户ID，分片键',
  party_type TINYINT NOT NULL COMMENT '参与方类型：1平台 2一级代理 3二级代理 4合伙人 5商户',
  party_id BIGINT NOT NULL COMMENT '参与方ID',
  amount DECIMAL(18,2) NOT NULL COMMENT '清分金额',
  direction TINYINT NOT NULL COMMENT '方向：1应收 2应付',
  create_time TIMESTAMP NOT NULL COMMENT '创建时间'
) COMMENT='清分明细';

-- 可靠消息发件箱
CREATE TABLE IF NOT EXISTS outbox_message (
  id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '主键ID',
  biz_key VARCHAR(64) NOT NULL COMMENT '业务键',
  merchant_id BIGINT NOT NULL COMMENT '商户ID，分片键',
  topic VARCHAR(64) NOT NULL COMMENT '消息主题',
  payload TEXT NOT NULL COMMENT '消息载荷(JSON)',
  status TINYINT NOT NULL COMMENT '发送状态：0待发送 1已发送',
  create_time TIMESTAMP NOT NULL COMMENT '创建时间'
) COMMENT='可靠消息Outbox';

-- 账务过账发件箱
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

-- 商户中间待结算账户
CREATE TABLE IF NOT EXISTS merchant_settle_account (
  account_id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '账户ID',
  merchant_id BIGINT NOT NULL UNIQUE COMMENT '商户ID',
  wait_balance DECIMAL(18,2) NOT NULL DEFAULT 0 COMMENT '待结算可用余额',
  frozen_balance DECIMAL(18,2) NOT NULL DEFAULT 0 COMMENT '提现冻结金额',
  settle_card_no VARCHAR(128) COMMENT '结算卡号(AES加密)',
  settle_mode TINYINT NOT NULL COMMENT '结算模式：1T1 2D0 3D1 4H0 5S0',
  version INT NOT NULL DEFAULT 0 COMMENT '乐观锁版本号',
  create_time TIMESTAMP NOT NULL COMMENT '创建时间',
  update_time TIMESTAMP NOT NULL COMMENT '更新时间'
) COMMENT='商户中间待结算账户';

-- 账户流水
CREATE TABLE IF NOT EXISTS account_flow (
  flow_id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '流水ID',
  merchant_id BIGINT NOT NULL COMMENT '商户ID',
  bill_no VARCHAR(64) COMMENT '关联账单号',
  settle_no VARCHAR(64) COMMENT '关联结算单号',
  op_type TINYINT NOT NULL COMMENT '操作类型：1清算入账 2提现冻结 3打款扣减 4失败解冻 5退款扣减',
  amount DECIMAL(18,2) NOT NULL COMMENT '变动金额',
  before_balance DECIMAL(18,2) NOT NULL COMMENT '变动前余额',
  after_balance DECIMAL(18,2) NOT NULL COMMENT '变动后余额',
  create_time TIMESTAMP NOT NULL COMMENT '创建时间',
  CONSTRAINT uk_biz UNIQUE (bill_no, op_type)
) COMMENT='账户流水';

-- 结算单
CREATE TABLE IF NOT EXISTS settlement_order (
  id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '主键ID',
  settle_no VARCHAR(64) NOT NULL UNIQUE COMMENT '结算单号',
  merchant_id BIGINT NOT NULL COMMENT '商户ID',
  settle_amount DECIMAL(18,2) NOT NULL COMMENT '结算金额',
  settle_mode TINYINT NOT NULL COMMENT '结算模式：1T1 2D0 3D1 4H0 5S0',
  settle_card_no VARCHAR(128) NOT NULL COMMENT '结算卡号',
  status TINYINT NOT NULL COMMENT '结算状态：0创建 1冻结 2出款中 3成功 4失败',
  channel_trade_no VARCHAR(64) COMMENT '银行渠道流水号',
  fail_reason VARCHAR(256) COMMENT '失败原因',
  origin_settle_no VARCHAR(64) COMMENT '原结算单号(重试/冲正)',
  create_time TIMESTAMP NOT NULL COMMENT '创建时间',
  update_time TIMESTAMP NOT NULL COMMENT '更新时间'
) COMMENT='结算单';

-- 提现申请
CREATE TABLE IF NOT EXISTS withdraw_apply (
  apply_id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '申请ID',
  apply_no VARCHAR(64) NOT NULL UNIQUE COMMENT '提现申请单号',
  merchant_id BIGINT NOT NULL COMMENT '商户ID',
  amount DECIMAL(18,2) NOT NULL COMMENT '提现金额',
  settle_no VARCHAR(64) COMMENT '关联结算单号',
  status TINYINT NOT NULL COMMENT '申请状态：0申请 1处理中 2成功 3失败',
  create_time TIMESTAMP NOT NULL COMMENT '创建时间'
) COMMENT='提现申请';

-- 商户应付挂账
CREATE TABLE IF NOT EXISTS merchant_payable_suspend (
  id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '主键ID',
  merchant_id BIGINT NOT NULL COMMENT '商户ID',
  bill_no VARCHAR(64) NOT NULL UNIQUE COMMENT '关联账单号',
  suspend_amount DECIMAL(18,2) NOT NULL COMMENT '挂账金额',
  settled_amount DECIMAL(18,2) NOT NULL DEFAULT 0 COMMENT '已结算金额',
  status TINYINT NOT NULL COMMENT '挂账状态：0挂账中 1已结清',
  create_time TIMESTAMP NOT NULL COMMENT '创建时间'
) COMMENT='商户应付挂账';

-- 会计凭证
CREATE TABLE IF NOT EXISTS account_voucher (
  voucher_id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '凭证ID',
  bill_no VARCHAR(64) NOT NULL COMMENT '关联账单号',
  merchant_id BIGINT NOT NULL COMMENT '商户ID，分片键',
  debit_subject VARCHAR(32) NOT NULL COMMENT '借方科目',
  credit_subject VARCHAR(32) NOT NULL COMMENT '贷方科目',
  amount DECIMAL(18,2) NOT NULL COMMENT '凭证金额',
  sync_status TINYINT NOT NULL DEFAULT 0 COMMENT 'ERP同步状态：0待同步 1已同步 2失败',
  create_time TIMESTAMP NOT NULL COMMENT '创建时间'
) COMMENT='会计凭证';

-- 商户对账单
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

-- 单据分片路由（config 库）
CREATE TABLE IF NOT EXISTS bill_route (
  bill_no VARCHAR(64) NOT NULL PRIMARY KEY COMMENT '清算单据号',
  merchant_id BIGINT NOT NULL COMMENT '商户ID，分片键',
  bill_type TINYINT NOT NULL COMMENT '单据类型',
  create_time TIMESTAMP NOT NULL COMMENT '创建时间'
) COMMENT='单据分片路由索引';

-- 结算单分片路由（config 库）
CREATE TABLE IF NOT EXISTS settle_route (
  settle_no VARCHAR(64) NOT NULL PRIMARY KEY COMMENT '结算单号',
  merchant_id BIGINT NOT NULL COMMENT '商户ID，分片键',
  create_time TIMESTAMP NOT NULL COMMENT '创建时间'
) COMMENT='结算单分片路由索引';

-- 告警记录
CREATE TABLE IF NOT EXISTS alert_record (
  alert_id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '告警ID',
  alert_type VARCHAR(32) NOT NULL COMMENT '告警类型',
  level TINYINT NOT NULL COMMENT '告警级别：1INFO 2WARN 3ERROR',
  content VARCHAR(512) NOT NULL COMMENT '告警内容',
  status TINYINT NOT NULL DEFAULT 0 COMMENT '处理状态：0未处理 1已处理',
  create_time TIMESTAMP NOT NULL COMMENT '创建时间'
) COMMENT='告警记录';

-- 异常工单（清算 DEAD / DLQ 等）
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
  create_time TIMESTAMP NOT NULL COMMENT '创建时间'
) COMMENT='异常工单';
