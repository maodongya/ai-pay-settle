#!/usr/bin/env bash
# 压测003：种子商户 1000001~1001000 + 多种结算模式/分润关系/计费规则
set -euo pipefail

MYSQL_CONTAINER="${MYSQL_CONTAINER:-mysql8}"
MYSQL_USER="${MYSQL_USER:-root}"
MYSQL_PASS="${MYSQL_PASS:-123456}"
START="${MERCHANT_ID_START:-1000001}"
END="${MERCHANT_ID_END:-1001000}"

mysql_exec() {
  docker exec -i "$MYSQL_CONTAINER" mysql -u"$MYSQL_USER" -p"$MYSQL_PASS" --default-character-set=utf8mb4 "$@"
}

echo ">>> seed fee_share_rule variants in pay_config"
mysql_exec pay_config <<'SQL'
TRUNCATE TABLE fee_share_rule;
-- target_type: 1平台 2一级代理 3二级代理 4合伙人
-- share_mode: 1固定比例 2固定金额 3阶梯递减
INSERT INTO fee_share_rule (rule_name, target_type, business_line, category, service_item, city_code, share_mode, first_month_value, step_down_val, min_share, valid_start, status, create_time) VALUES
('Platform default 0.6%', 1, '*', '*', '*', '*', 1, 0.0060, 0, 0, '2026-01-01 00:00:00', 1, CURRENT_TIMESTAMP),
('Platform lineA 0.8%', 1, 'A', '*', '*', '*', 1, 0.0080, 0, 0, '2026-01-01 00:00:00', 1, CURRENT_TIMESTAMP),
('Platform fixed 1.00', 1, 'B', '*', '*', '*', 2, 1.0000, 0, 0, '2026-01-01 00:00:00', 1, CURRENT_TIMESTAMP),
('Agent L1 default 5%', 2, '*', '*', '*', '*', 1, 0.0500, 0, 0, '2026-01-01 00:00:00', 1, CURRENT_TIMESTAMP),
('Agent L1 lineA 4%', 2, 'A', '*', '*', '*', 1, 0.0400, 0, 0, '2026-01-01 00:00:00', 1, CURRENT_TIMESTAMP),
('Agent L2 default 3%', 3, '*', '*', '*', '*', 1, 0.0300, 0, 0, '2026-01-01 00:00:00', 1, CURRENT_TIMESTAMP),
('Agent L2 lineA 2%', 3, 'A', '*', '*', '*', 1, 0.0200, 0, 0, '2026-01-01 00:00:00', 1, CURRENT_TIMESTAMP),
('Partner step down', 4, '*', '*', '*', '*', 3, 0.0200, 0.0010, 0.0050, '2026-01-01 00:00:00', 1, CURRENT_TIMESTAMP),
('Partner fixed rate 1.5%', 4, 'A', '*', '*', '*', 1, 0.0150, 0, 0, '2026-01-01 00:00:00', 1, CURRENT_TIMESTAMP);
SQL

echo ">>> seed merchant_profile/contract/relation ($START..$END)"
# 分批写入，避免单次 SQL 过大
batch_sql=""
batch_count=0
flush_batch() {
  if [[ -n "$batch_sql" ]]; then
    printf '%s\n' "$batch_sql" | mysql_exec pay_config
    batch_sql=""
    batch_count=0
  fi
}

for ((id=START; id<=END; id++)); do
  offset=$((id - START))
  # settle_mode 轮转: 1T1 2D0 3D1 4H0 5S0
  settle_mode=$(( (offset % 5) + 1 ))
  # 分润关系 4 类轮转:
  # 0: 平台费 only（无代理/合伙人）
  # 1: 仅一级代理
  # 2: 一级+二级代理
  # 3: 一级+二级+合伙人（完整链路）
  rel_type=$((offset % 4))
  agent_bucket=$((offset % 10))
  agent_id=$((210000 + agent_bucket))
  second_agent_id=$((220000 + agent_bucket))
  partner_id=$((310000 + (offset % 20)))

  batch_sql+="INSERT INTO merchant_profile (merchant_id, merchant_name, status) VALUES ($id, 'LT003 Merchant $id', 1);"
  batch_sql+="INSERT INTO merchant_contract (merchant_id, sign_date, settle_mode, min_withdraw, status) VALUES ($id, '2026-01-01', $settle_mode, 100.00, 1);"

  case $rel_type in
    0)
      # 无代理关系：计费仅打平台手续费
      ;;
    1)
      batch_sql+="INSERT INTO agent_merchant_relation (agent_id, second_agent_id, merchant_id, split_party_id, valid_start, valid_end) VALUES ($agent_id, NULL, $id, NULL, '2026-01-01 00:00:00', NULL);"
      ;;
    2)
      batch_sql+="INSERT INTO agent_merchant_relation (agent_id, second_agent_id, merchant_id, split_party_id, valid_start, valid_end) VALUES ($agent_id, $second_agent_id, $id, NULL, '2026-01-01 00:00:00', NULL);"
      ;;
    3)
      batch_sql+="INSERT INTO agent_merchant_relation (agent_id, second_agent_id, merchant_id, split_party_id, valid_start, valid_end) VALUES ($agent_id, $second_agent_id, $id, $partner_id, '2026-01-01 00:00:00', NULL);"
      ;;
  esac

  batch_count=$((batch_count + 1))
  if (( batch_count >= 50 )); then
    flush_batch
  fi
done
flush_batch

echo ">>> seed merchant_settle_account into physical shard tables"
batch_sql=""
batch_count=0
flush_data_batch() {
  if [[ -n "$batch_sql" ]]; then
    printf '%s\n' "$batch_sql" | mysql_exec
    batch_sql=""
    batch_count=0
  fi
}

for ((id=START; id<=END; id++)); do
  offset=$((id - START))
  settle_mode=$(( (offset % 5) + 1 ))
  shard=$((id % 16))
  db=$((shard / 4))
  suffix=$((shard % 4))
  dbname=$(printf 'pay_data_%02d' "$db")
  table="merchant_settle_account_${suffix}"
  card=$(printf '6222%012d' "$id")
  batch_sql+="INSERT INTO ${dbname}.${table} (merchant_id, wait_balance, frozen_balance, settle_card_no, settle_mode, version, create_time, update_time) VALUES ($id, 0.00, 0.00, '$card', $settle_mode, 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);"
  batch_count=$((batch_count + 1))
  if (( batch_count >= 50 )); then
    flush_data_batch
  fi
done
flush_data_batch

echo ">>> verify"
mysql_exec -e "
SELECT COUNT(*) AS profile_cnt FROM pay_config.merchant_profile WHERE merchant_id BETWEEN $START AND $END;
SELECT settle_mode, COUNT(*) AS cnt FROM pay_config.merchant_contract WHERE merchant_id BETWEEN $START AND $END GROUP BY settle_mode ORDER BY settle_mode;
SELECT
  CASE
    WHEN agent_id IS NULL THEN 'none'
    WHEN second_agent_id IS NULL AND split_party_id IS NULL THEN 'L1_only'
    WHEN split_party_id IS NULL THEN 'L1_L2'
    ELSE 'L1_L2_PARTNER'
  END AS rel_type,
  COUNT(*) AS cnt
FROM (
  SELECT m.merchant_id, r.agent_id, r.second_agent_id, r.split_party_id
  FROM pay_config.merchant_profile m
  LEFT JOIN pay_config.agent_merchant_relation r ON m.merchant_id = r.merchant_id
  WHERE m.merchant_id BETWEEN $START AND $END
) x
GROUP BY rel_type
ORDER BY rel_type;
SELECT COUNT(*) AS fee_rule_cnt FROM pay_config.fee_share_rule WHERE status=1;
SELECT COUNT(*) AS settle_account_cnt FROM (
  SELECT merchant_id FROM pay_data_00.merchant_settle_account_0 WHERE merchant_id BETWEEN $START AND $END
  UNION ALL SELECT merchant_id FROM pay_data_00.merchant_settle_account_1 WHERE merchant_id BETWEEN $START AND $END
  UNION ALL SELECT merchant_id FROM pay_data_00.merchant_settle_account_2 WHERE merchant_id BETWEEN $START AND $END
  UNION ALL SELECT merchant_id FROM pay_data_00.merchant_settle_account_3 WHERE merchant_id BETWEEN $START AND $END
  UNION ALL SELECT merchant_id FROM pay_data_01.merchant_settle_account_0 WHERE merchant_id BETWEEN $START AND $END
  UNION ALL SELECT merchant_id FROM pay_data_01.merchant_settle_account_1 WHERE merchant_id BETWEEN $START AND $END
  UNION ALL SELECT merchant_id FROM pay_data_01.merchant_settle_account_2 WHERE merchant_id BETWEEN $START AND $END
  UNION ALL SELECT merchant_id FROM pay_data_01.merchant_settle_account_3 WHERE merchant_id BETWEEN $START AND $END
  UNION ALL SELECT merchant_id FROM pay_data_02.merchant_settle_account_0 WHERE merchant_id BETWEEN $START AND $END
  UNION ALL SELECT merchant_id FROM pay_data_02.merchant_settle_account_1 WHERE merchant_id BETWEEN $START AND $END
  UNION ALL SELECT merchant_id FROM pay_data_02.merchant_settle_account_2 WHERE merchant_id BETWEEN $START AND $END
  UNION ALL SELECT merchant_id FROM pay_data_02.merchant_settle_account_3 WHERE merchant_id BETWEEN $START AND $END
  UNION ALL SELECT merchant_id FROM pay_data_03.merchant_settle_account_0 WHERE merchant_id BETWEEN $START AND $END
  UNION ALL SELECT merchant_id FROM pay_data_03.merchant_settle_account_1 WHERE merchant_id BETWEEN $START AND $END
  UNION ALL SELECT merchant_id FROM pay_data_03.merchant_settle_account_2 WHERE merchant_id BETWEEN $START AND $END
  UNION ALL SELECT merchant_id FROM pay_data_03.merchant_settle_account_3 WHERE merchant_id BETWEEN $START AND $END
) x;
SELECT (merchant_id % 16) AS shard_id, COUNT(*) AS cnt
FROM pay_config.merchant_profile
WHERE merchant_id BETWEEN $START AND $END
GROUP BY (merchant_id % 16)
ORDER BY shard_id;
"
echo ">>> done"
