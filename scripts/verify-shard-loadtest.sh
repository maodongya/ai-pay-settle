#!/usr/bin/env bash
# 压测后核对 10001~10100 在各物理分表上的账单分布
set -euo pipefail

MYSQL_CONTAINER="${MYSQL_CONTAINER:-mysql8}"
MYSQL_USER="${MYSQL_USER:-root}"
MYSQL_PASS="${MYSQL_PASS:-123456}"
START="${MERCHANT_ID_START:-10001}"
END="${MERCHANT_ID_END:-10100}"

mysql_q() {
  docker exec -i "$MYSQL_CONTAINER" mysql -u"$MYSQL_USER" -p"$MYSQL_PASS" -N -e "$1" 2>/dev/null | tr -d '\r'
}

echo "=== bill_route (pay_config) ==="
docker exec -i "$MYSQL_CONTAINER" mysql -u"$MYSQL_USER" -p"$MYSQL_PASS" -e "
SELECT COUNT(*) AS route_cnt,
       COUNT(DISTINCT merchant_id) AS merchants
FROM pay_config.bill_route
WHERE merchant_id BETWEEN $START AND $END;
SELECT (merchant_id % 16) AS shard_id, COUNT(*) AS bills
FROM pay_config.bill_route
WHERE merchant_id BETWEEN $START AND $END
GROUP BY (merchant_id % 16) ORDER BY shard_id;
" 2>/dev/null

echo "=== trade_bill physical tables ==="
total_bills=0
for db in 00 01 02 03; do
  for suffix in 0 1 2 3; do
    cnt=$(mysql_q "SELECT COUNT(*) FROM pay_data_${db}.trade_bill_${suffix} WHERE merchant_id BETWEEN $START AND $END;")
    cnt=${cnt:-0}
    total_bills=$((total_bills + cnt))
    echo "pay_data_${db}.trade_bill_${suffix}: ${cnt}"
  done
done
echo "trade_bill_total: $total_bills"

echo "=== clearance_task SUCCESS by db/table ==="
total_ok=0
for db in 00 01 02 03; do
  for suffix in 0 1 2 3; do
    cnt=$(mysql_q "SELECT COUNT(*) FROM pay_data_${db}.clearance_task_${suffix} WHERE merchant_id BETWEEN $START AND $END AND status=2;")
    cnt=${cnt:-0}
    total_ok=$((total_ok + cnt))
    echo "pay_data_${db}.clearance_task_${suffix} SUCCESS: ${cnt}"
  done
done
echo "clearance_SUCCESS_total: $total_ok"

echo "=== outbox dispatched (status=1) ==="
total_out=0
for db in 00 01 02 03; do
  for suffix in 0 1 2 3; do
    cnt=$(mysql_q "SELECT COUNT(*) FROM pay_data_${db}.outbox_message_${suffix} WHERE merchant_id BETWEEN $START AND $END AND status=1;")
    cnt=${cnt:-0}
    total_out=$((total_out + cnt))
    echo "pay_data_${db}.outbox_message_${suffix} dispatched: ${cnt}"
  done
done
echo "outbox_dispatched_total: $total_out"

echo "=== settle account wait_balance > 0 merchants ==="
total=0
for db in 00 01 02 03; do
  for suffix in 0 1 2 3; do
    cnt=$(mysql_q "SELECT COUNT(*) FROM pay_data_${db}.merchant_settle_account_${suffix} WHERE merchant_id BETWEEN $START AND $END AND wait_balance > 0;")
    cnt=${cnt:-0}
    total=$((total + cnt))
    echo "pay_data_${db}.merchant_settle_account_${suffix} credited: ${cnt}"
  done
done
echo "merchants_with_wait_balance: $total"
