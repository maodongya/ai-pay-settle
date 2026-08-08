#!/usr/bin/env bash
# 压测过程003：保活 pay-app + 30 分钟 30 TPS MQ 压测（商户 1000001~1001000）
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
APP_JAR="$ROOT/pay-app/target/pay-app-1.0.0-SNAPSHOT.jar"
APP_LOG=/tmp/pay-app-loadtest003.log
LT_LOG=/tmp/mq-loadtest-003.log
WATCH_LOG=/tmp/loadtest003-watchdog.log
RESULT_DIR=/tmp/loadtest003-result
MERCHANT_START="${MERCHANT_ID_START:-1000001}"
MERCHANT_END="${MERCHANT_ID_END:-1001000}"
DURATION="${DURATION:-1800}"
TPS="${TPS:-30}"
CLIENTS="${CLIENTS:-5}"
mkdir -p "$RESULT_DIR"

is_app_alive() {
  pgrep -f 'pay-app-1.0.0-SNAPSHOT.jar' >/dev/null 2>&1
}

health_ok() {
  curl -sf http://127.0.0.1:18089/actuator/health >/dev/null 2>&1
}

start_app() {
  if health_ok; then
    return 0
  fi
  if is_app_alive; then
    # 进程在但 health 暂不可用：等待恢复，避免误杀打断压测
    for i in $(seq 1 30); do
      if health_ok; then
        return 0
      fi
      sleep 2
    done
  fi
  pkill -9 -f 'pay-app-1.0.0-SNAPSHOT.jar' 2>/dev/null || true
  sleep 2
  nohup java -Xms512m -Xmx1536m -XX:+ExitOnOutOfMemoryError \
    -jar "$APP_JAR" \
    --spring.profiles.active=mysql,sharding,mq \
    --pay.mq.backlog.circuit-enabled=false \
    --pay.loadtest.pause-jobs=true \
    --pay.clearance.retry-interval-ms=3600000 \
    --pay.compensate.outbox-interval-ms=3600000 \
    >"$APP_LOG" 2>&1 &
  disown || true
  echo "$(date '+%F %T') started app pid=$!" | tee -a "$WATCH_LOG"
  for i in $(seq 1 90); do
    if health_ok; then
      echo "$(date '+%F %T') health UP" | tee -a "$WATCH_LOG"
      return 0
    fi
    sleep 2
  done
  echo "$(date '+%F %T') health FAIL" | tee -a "$WATCH_LOG"
  return 1
}

sample_once() {
  local tag="$1"
  local routes access_diff health=UP
  routes=$(docker exec mysql8 mysql -uroot -p123456 -N -e \
    "SELECT COUNT(*) FROM pay_config.bill_route WHERE merchant_id BETWEEN $MERCHANT_START AND $MERCHANT_END;" 2>/dev/null || echo -1)
  access_diff=$(docker exec rmq-broker sh -c 'cd /home/rocketmq/rocketmq-4.9.6 && sh bin/mqadmin consumerProgress -n rmq-namesrv:9876 -g pay-access-consumer 2>/dev/null' \
    | awk '/trade_pay_topic/{s+=$NF} END{print s+0}')
  health_ok || health=DOWN
  echo "$(date '+%F %T') SAMPLE[$tag] health=$health routes=$routes accessDiff=$access_diff appAlive=$(is_app_alive && echo 1 || echo 0)" | tee -a "$WATCH_LOG"
}

reset_mq_offsets() {
  echo "$(date '+%F %T') reset MQ offsets" | tee -a "$WATCH_LOG"
  docker exec rmq-broker sh -c '
cd /home/rocketmq/rocketmq-4.9.6
for pair in \
  "pay-access-consumer:trade_pay_topic" \
  "pay-access-consumer:%RETRY%pay-access-consumer" \
  "pay-access-refund-consumer:trade_refund_topic" \
  "pay-access-refund-consumer:%RETRY%pay-access-refund-consumer" \
  "pay-calc-consumer:clearance_task_topic" \
  "pay-calc-consumer:%RETRY%pay-calc-consumer" \
  "pay-settlement-consumer:settle_amount_topic"
do
  g=${pair%%:*}; t=${pair#*:}
  sh bin/mqadmin resetOffsetByTime -n rmq-namesrv:9876 -g "$g" -t "$t" -s now -f true >/dev/null 2>&1 || true
done
' || true
}

echo "=== loadtest 003 prepare $(date '+%F %T') ===" | tee "$WATCH_LOG"

pkill -f 'com.payment.test.mq.MqLoadTestMain' 2>/dev/null || true
sleep 1

start_app
sleep 5
reset_mq_offsets
sleep 5
docker exec rmq-broker sh -c 'cd /home/rocketmq/rocketmq-4.9.6 && sh bin/mqadmin consumerProgress -n rmq-namesrv:9876 2>/dev/null' \
  | head -12 | tee -a "$WATCH_LOG" || true

BASE_ROUTES=$(docker exec mysql8 mysql -uroot -p123456 -N -e \
  "SELECT COUNT(*) FROM pay_config.bill_route WHERE merchant_id BETWEEN $MERCHANT_START AND $MERCHANT_END;" 2>/dev/null || echo 0)
echo "$BASE_ROUTES" > "$RESULT_DIR/base-routes.txt"
echo "BASE_ROUTES=$BASE_ROUTES" | tee -a "$WATCH_LOG"

# sampler only（不随意杀进程）；仅当 app 进程消失才重启
(
  while true; do
    if ! is_app_alive; then
      echo "$(date '+%F %T') watchdog app dead -> restart" >>"$WATCH_LOG"
      start_app || true
    fi
    sample_once tick
    sleep 60
  done
) &
WATCH_PID=$!
echo "WATCH_PID=$WATCH_PID" | tee -a "$WATCH_LOG"

# Producer 独立 nohup，避免编排 shell 回收时连带退出
cd "$ROOT"
: >"$LT_LOG"
nohup env NAME_SERVER=127.0.0.1:9877 CLIENTS="$CLIENTS" TPS="$TPS" DURATION="$DURATION" \
  MERCHANT_ID_START="$MERCHANT_START" MERCHANT_ID_END="$MERCHANT_END" \
  ./pay-test/run-mq-load-test.sh --report-interval-seconds=60 \
  >"$LT_LOG" 2>&1 &
LT_PID=$!
disown || true
echo "LT_PID=$LT_PID" | tee -a "$WATCH_LOG"

# 等待压测结束（最多 duration+120s）
deadline=$((SECONDS + DURATION + 180))
LT_EXIT=0
while kill -0 "$LT_PID" 2>/dev/null; do
  if (( SECONDS >= deadline )); then
    echo "$(date '+%F %T') producer timeout, kill $LT_PID" | tee -a "$WATCH_LOG"
    kill "$LT_PID" 2>/dev/null || true
    LT_EXIT=124
    break
  fi
  sleep 5
done
if [[ "$LT_EXIT" -eq 0 ]]; then
  wait "$LT_PID" || LT_EXIT=$?
fi
echo "LOADTEST_EXIT=$LT_EXIT" | tee -a "$WATCH_LOG"
kill "$WATCH_PID" 2>/dev/null || true

echo "draining 90s..." | tee -a "$WATCH_LOG"
sleep 90
sample_once final

cp "$LT_LOG" "$RESULT_DIR/mq-loadtest.log"
cp "$APP_LOG" "$RESULT_DIR/app.log" 2>/dev/null || true
cp "$WATCH_LOG" "$RESULT_DIR/watchdog.log"
docker exec rmq-broker sh -c 'cd /home/rocketmq/rocketmq-4.9.6 && sh bin/mqadmin consumerProgress -n rmq-namesrv:9876 2>/dev/null' \
  | head -20 >"$RESULT_DIR/mq-progress.txt" || true

END_ROUTES=$(docker exec mysql8 mysql -uroot -p123456 -N -e \
  "SELECT COUNT(*) FROM pay_config.bill_route WHERE merchant_id BETWEEN $MERCHANT_START AND $MERCHANT_END;" 2>/dev/null || echo 0)
echo "$END_ROUTES" >"$RESULT_DIR/end-routes.txt"
echo "END_ROUTES=$END_ROUTES DELTA=$((END_ROUTES-BASE_ROUTES))" | tee -a "$WATCH_LOG"

{
  echo "=== bill_route by shard ==="
  docker exec mysql8 mysql -uroot -p123456 -e "
SELECT (merchant_id % 16) AS shard_id, COUNT(*) AS cnt, COUNT(DISTINCT merchant_id) AS merchants
FROM pay_config.bill_route
WHERE merchant_id BETWEEN $MERCHANT_START AND $MERCHANT_END
GROUP BY (merchant_id % 16) ORDER BY shard_id;" 2>/dev/null

  echo "=== trade_bill physical ==="
  total_bill=0
  for db in 00 01 02 03; do
    for s in 0 1 2 3; do
      n=$(docker exec mysql8 mysql -uroot -p123456 -N -e \
        "SELECT COUNT(*) FROM pay_data_${db}.trade_bill_${s} WHERE merchant_id BETWEEN $MERCHANT_START AND $MERCHANT_END;" 2>/dev/null)
      echo "pay_data_${db}.trade_bill_${s}=$n"
      total_bill=$((total_bill+n))
    done
  done
  echo "trade_bill_total=$total_bill"

  echo "=== clearance SUCCESS ==="
  total=0
  for db in 00 01 02 03; do
    for s in 0 1 2 3; do
      n=$(docker exec mysql8 mysql -uroot -p123456 -N -e \
        "SELECT COUNT(*) FROM pay_data_${db}.clearance_task_${s} WHERE merchant_id BETWEEN $MERCHANT_START AND $MERCHANT_END AND status=2;" 2>/dev/null)
      echo "pay_data_${db}.clearance_task_${s}=$n"
      total=$((total+n))
    done
  done
  echo "clearance_SUCCESS_total=$total"

  echo "=== outbox dispatched status=1 ==="
  outbox=0
  for db in 00 01 02 03; do
    for s in 0 1 2 3; do
      n=$(docker exec mysql8 mysql -uroot -p123456 -N -e \
        "SELECT COUNT(*) FROM pay_data_${db}.outbox_message_${s} WHERE merchant_id BETWEEN $MERCHANT_START AND $MERCHANT_END AND status=1;" 2>/dev/null)
      outbox=$((outbox+n))
    done
  done
  echo "outbox_dispatched=$outbox"

  echo "=== merchants with wait_balance>0 ==="
  credited=0
  for db in 00 01 02 03; do
    for s in 0 1 2 3; do
      n=$(docker exec mysql8 mysql -uroot -p123456 -N -e \
        "SELECT COUNT(*) FROM pay_data_${db}.merchant_settle_account_${s} WHERE merchant_id BETWEEN $MERCHANT_START AND $MERCHANT_END AND wait_balance>0;" 2>/dev/null)
      credited=$((credited+n))
    done
  done
  echo "merchants_credited=$credited"

  echo "=== distinct merchants in bill_route ==="
  docker exec mysql8 mysql -uroot -p123456 -N -e \
    "SELECT COUNT(DISTINCT merchant_id) FROM pay_config.bill_route WHERE merchant_id BETWEEN $MERCHANT_START AND $MERCHANT_END;" 2>/dev/null

  echo "=== settle_mode coverage in billed merchants ==="
  docker exec mysql8 mysql -uroot -p123456 -e "
SELECT c.settle_mode, COUNT(DISTINCT r.merchant_id) AS merchants, COUNT(*) AS bills
FROM pay_config.bill_route r
JOIN pay_config.merchant_contract c ON r.merchant_id=c.merchant_id
WHERE r.merchant_id BETWEEN $MERCHANT_START AND $MERCHANT_END
GROUP BY c.settle_mode ORDER BY c.settle_mode;" 2>/dev/null

  echo "=== relation coverage in billed merchants ==="
  docker exec mysql8 mysql -uroot -p123456 -e "
SELECT
  CASE
    WHEN a.agent_id IS NULL THEN 'none'
    WHEN a.second_agent_id IS NULL AND a.split_party_id IS NULL THEN 'L1_only'
    WHEN a.split_party_id IS NULL THEN 'L1_L2'
    ELSE 'L1_L2_PARTNER'
  END AS rel_type,
  COUNT(DISTINCT r.merchant_id) AS merchants,
  COUNT(*) AS bills
FROM pay_config.bill_route r
LEFT JOIN pay_config.agent_merchant_relation a ON r.merchant_id=a.merchant_id
WHERE r.merchant_id BETWEEN $MERCHANT_START AND $MERCHANT_END
GROUP BY rel_type ORDER BY rel_type;" 2>/dev/null

  echo "=== app error counters ==="
  if [[ -f "$APP_LOG" ]]; then
    echo "lock_wait=$(grep -c 'Lock wait timeout' "$APP_LOG" || true)"
    echo "conn_timeout=$(grep -c 'Connection is not available' "$APP_LOG" || true)"
    echo "trade_pay_fail=$(grep -c 'trade pay consume failed' "$APP_LOG" || true)"
    echo "clearance_fail=$(grep -c 'clearance failed' "$APP_LOG" || true)"
    echo "data_too_long=$(grep -c 'Data too long' "$APP_LOG" || true)"
    echo "consumer_threads_applied=$(grep -c 'MQ consumer threads applied' "$APP_LOG" || true)"
  fi
} | tee "$RESULT_DIR/verify.txt"

echo "=== done $(date '+%F %T') ===" | tee -a "$WATCH_LOG"
exit "$LT_EXIT"
