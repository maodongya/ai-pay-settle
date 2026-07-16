#!/usr/bin/env bash
# PR-C2：隔离历史 DEAD 清算任务，统计终态积压，供 drain 前评估。
# 说明：DEAD 任务不会被 RetryJob 重投；配合消费端 fast-skip 与 MQ offset 重置使用。
set -euo pipefail

MYSQL="${MYSQL_CMD:-docker exec mysql8 mysql -uroot -p123456 -Nse}"

count_status() {
  local status="$1"
  $MYSQL "
    SELECT SUM(c) FROM (
      SELECT COUNT(*) c FROM pay_data_00.clearance_task_0 WHERE status=$status
      UNION ALL SELECT COUNT(*) FROM pay_data_00.clearance_task_1 WHERE status=$status
      UNION ALL SELECT COUNT(*) FROM pay_data_00.clearance_task_2 WHERE status=$status
      UNION ALL SELECT COUNT(*) FROM pay_data_00.clearance_task_3 WHERE status=$status
      UNION ALL SELECT COUNT(*) FROM pay_data_01.clearance_task_0 WHERE status=$status
      UNION ALL SELECT COUNT(*) FROM pay_data_01.clearance_task_1 WHERE status=$status
      UNION ALL SELECT COUNT(*) FROM pay_data_01.clearance_task_2 WHERE status=$status
      UNION ALL SELECT COUNT(*) FROM pay_data_01.clearance_task_3 WHERE status=$status
      UNION ALL SELECT COUNT(*) FROM pay_data_02.clearance_task_0 WHERE status=$status
      UNION ALL SELECT COUNT(*) FROM pay_data_02.clearance_task_1 WHERE status=$status
      UNION ALL SELECT COUNT(*) FROM pay_data_02.clearance_task_2 WHERE status=$status
      UNION ALL SELECT COUNT(*) FROM pay_data_02.clearance_task_3 WHERE status=$status
      UNION ALL SELECT COUNT(*) FROM pay_data_03.clearance_task_0 WHERE status=$status
      UNION ALL SELECT COUNT(*) FROM pay_data_03.clearance_task_1 WHERE status=$status
      UNION ALL SELECT COUNT(*) FROM pay_data_03.clearance_task_2 WHERE status=$status
      UNION ALL SELECT COUNT(*) FROM pay_data_03.clearance_task_3 WHERE status=$status
    ) t;
  " 2>/dev/null
}

echo "== clearance_task status snapshot =="
echo "PENDING(0):  $(count_status 0)"
echo "RUNNING(1): $(count_status 1)"
echo "SUCCESS(2): $(count_status 2)"
echo "FAILED(3):  $(count_status 3)"
echo "DEAD(4):    $(count_status 4)"
echo ""
echo "Tip: drain 前执行 scripts/reset-calc-mq-offset.sh，并确保应用已启用 PR-C2 fast-skip。"
