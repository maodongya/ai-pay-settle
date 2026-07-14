#!/usr/bin/env bash
# 在 pay_config / pay_data_* 写入商户 10001~10100（分库分表压测用）
set -euo pipefail

MYSQL_CONTAINER="${MYSQL_CONTAINER:-mysql8}"
MYSQL_USER="${MYSQL_USER:-root}"
MYSQL_PASS="${MYSQL_PASS:-123456}"
START="${MERCHANT_ID_START:-10001}"
END="${MERCHANT_ID_END:-10100}"

mysql_exec() {
  docker exec -i "$MYSQL_CONTAINER" mysql -u"$MYSQL_USER" -p"$MYSQL_PASS" --default-character-set=utf8mb4 "$@"
}

echo ">>> seed merchant_profile/contract in pay_config ($START..$END)"
sql_config=""
for ((id=START; id<=END; id++)); do
  sql_config+="INSERT IGNORE INTO merchant_profile (merchant_id, merchant_name, status) VALUES ($id, 'LoadTest Merchant $id', 1);"
  sql_config+="INSERT IGNORE INTO merchant_contract (merchant_id, sign_date, settle_mode, min_withdraw, status) VALUES ($id, '2026-01-01', 2, 100.00, 1);"
  sql_config+="INSERT IGNORE INTO agent_merchant_relation (agent_id, second_agent_id, merchant_id, split_party_id, valid_start, valid_end) VALUES (200001, 200002, $id, 300001, '2026-01-01 00:00:00', NULL);"
done
printf '%s\n' "$sql_config" | mysql_exec pay_config

echo ">>> seed merchant_settle_account into physical shard tables"
sql_data=""
for ((id=START; id<=END; id++)); do
  shard=$((id % 16))
  db=$((shard / 4))
  suffix=$((shard % 4))
  dbname=$(printf 'pay_data_%02d' "$db")
  table="merchant_settle_account_${suffix}"
  card=$(printf '6222%012d' "$id")
  sql_data+="INSERT IGNORE INTO ${dbname}.${table} (merchant_id, wait_balance, frozen_balance, settle_card_no, settle_mode, version, create_time, update_time) VALUES ($id, 0.00, 0.00, '$card', 2, 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP);"
done
printf '%s\n' "$sql_data" | mysql_exec

echo ">>> verify"
mysql_exec -e "
SELECT COUNT(*) AS profile_cnt FROM pay_config.merchant_profile WHERE merchant_id BETWEEN $START AND $END;
SELECT
  CONCAT('pay_data_', LPAD(FLOOR((merchant_id % 16) / 4), 2, '0'), '.merchant_settle_account_', (merchant_id % 16) % 4) AS physical_hint,
  COUNT(*) AS cnt
FROM (
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
) t
GROUP BY 1
ORDER BY 1;
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
"
echo ">>> done"
