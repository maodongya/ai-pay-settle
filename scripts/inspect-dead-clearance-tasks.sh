#!/usr/bin/env bash
# C2-d：按分片统计 clearance_task DEAD 存量（只读巡检，不改数据）
# Retry Job 只扫 FAILED；DEAD 需运营台/人工重放，勿与 MQ 双通道重复投递。
set -euo pipefail

MYSQL_CONTAINER="${MYSQL_CONTAINER:-mysql8}"
MYSQL_USER="${MYSQL_USER:-root}"
MYSQL_PASS="${MYSQL_PASS:-123456}"

echo ">>> DEAD clearance_task counts by physical table"
total=0
for db in 00 01 02 03; do
  for s in 0 1 2 3; do
    n=$(docker exec "$MYSQL_CONTAINER" mysql -u"$MYSQL_USER" -p"$MYSQL_PASS" -N -e \
      "SELECT COUNT(*) FROM pay_data_${db}.clearance_task_${s} WHERE status=4;" 2>/dev/null || echo 0)
    echo "pay_data_${db}.clearance_task_${s} DEAD=$n"
    total=$((total + n))
  done
done
echo ">>> TOTAL_DEAD=$total"
echo ">>> note: ClearanceRetryJob only scans FAILED; DEAD → exception_record / 运营台"
