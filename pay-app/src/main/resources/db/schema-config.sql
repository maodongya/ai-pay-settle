-- pay_config 配置库表（不分片）

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

CREATE TABLE IF NOT EXISTS agent_merchant_relation (
  rel_id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '关系ID',
  agent_id BIGINT NOT NULL COMMENT '一级代理ID',
  second_agent_id BIGINT COMMENT '二级代理ID',
  merchant_id BIGINT NOT NULL COMMENT '商户ID',
  split_party_id BIGINT COMMENT '合伙人/分账方ID',
  valid_start TIMESTAMP NOT NULL COMMENT '关系生效时间',
  valid_end TIMESTAMP COMMENT '关系失效时间'
) COMMENT='代理-商户-分润关系';

CREATE TABLE IF NOT EXISTS merchant_profile (
  merchant_id BIGINT PRIMARY KEY COMMENT '商户ID',
  merchant_name VARCHAR(128) NOT NULL COMMENT '商户名称',
  status TINYINT NOT NULL COMMENT '商户状态：0冻结 1正常'
) COMMENT='商户档案';

CREATE TABLE IF NOT EXISTS merchant_contract (
  contract_id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '合同ID',
  merchant_id BIGINT NOT NULL UNIQUE COMMENT '商户ID',
  sign_date DATE NOT NULL COMMENT '签约日期',
  settle_mode TINYINT NOT NULL COMMENT '结算模式：1T1 2D0 3D1 4H0 5S0',
  min_withdraw DECIMAL(18,2) NOT NULL COMMENT '最低提现金额',
  status TINYINT NOT NULL COMMENT '合同状态：0失效 1有效'
) COMMENT='商户签约合同';

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

CREATE TABLE IF NOT EXISTS alert_record (
  alert_id BIGINT AUTO_INCREMENT PRIMARY KEY COMMENT '告警ID',
  alert_type VARCHAR(32) NOT NULL COMMENT '告警类型',
  level TINYINT NOT NULL COMMENT '告警级别：1INFO 2WARN 3ERROR',
  content VARCHAR(512) NOT NULL COMMENT '告警内容',
  status TINYINT NOT NULL DEFAULT 0 COMMENT '处理状态：0未处理 1已处理',
  create_time TIMESTAMP NOT NULL COMMENT '创建时间'
) COMMENT='告警记录';

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
