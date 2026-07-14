#!/usr/bin/env bash
# 压测过程002：保活 pay-app + 10 分钟 30 TPS MQ 压测（商户 10001~10100）
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
APP_JAR="$ROOT/pay-app/target/pay-app-1.0.0-SNAPSHOT.jar"
APP_LOG=/tmp/pay-app-loadtest002.log
LT_LOG=/tmp/mq-loadtest-002.log
WATCH_LOG=/tmp/loadtest002-watchdog.log
RESULT_DIR=/tmp/loadtest002-result
mkdir -p "$RESULT_DIR"

start_app() {
  if curl -sf http://127.0.0.1:18089/actuator/health >/dev/null 2>&1; then
    return 0
  fi
  pkill -9 -f 'pay-app-1.0.0-SNAPSHOT.jar' 2>/dev/null || true
  sleep 2
  nohup java -Xms512m -Xmx1536m -XX:+ExitOnOutOfMemoryError \
    -jar "$APP_JAR" \
    --spring.profiles.active=mysql,sharding,mq \
    --pay.mq.backlog.circuit-enabled=false \
    >"$APP_LOG" 2>&1 &
  echo "$(date '+%F %T') started app pid=$!" | tee -a "$WATCH_LOG"
  for i in $(seq 1 60); do
    if curl -sf http://127.0.0.1:18089/actuator/health >/dev/null 2>&1; then
      echo "$(date '+%F %T') health UP" | tee -a "$WATCH_LOG"
      return 0
    fi
    sleep 2
  done
  echo "$(date '+%F %T') health FAIL" | tee -a "$WATCH_LOG"
  return 1
}

echo "=== loadtest 002 prepare $(date '+%F %T') ===" | tee "$WATCH_LOG"

# stop previous producer
pkill -f 'com.payment.test.mq.MqLoadTestMain' 2>/dev/null || true
sleep 1

# reset MQ
docker exec rmq-broker sh -c '
cd /home/rocketmq/rocketmq-4.9.6
for pair in \
  "pay-access-consumer:trade_pay_topic" \
  "pay-access-consumer:%RETRY%pay-access-consumer" \
  "pay-calc-consumer:clearance_task_topic" \
  "pay-calc-consumer:%RETRY%pay-calc-consumer" \
  "pay-settlement-consumer:settle_amount_topic"
do
  g=${pair%%:*}; t=${pair#*:}
  sh bin/mqadmin resetOffsetByTime -n rmq-namesrv:9876 -g "$g" -t "$t" -s now -f true >/dev/null
done
'

start_app
sleep 3
docker exec rmq-broker sh -c 'cd /home/rocketmq/rocketmq-4.9.6 && sh bin/mqadmin consumerProgress -n rmq-namesrv:9876 2>/dev/null' | head -8 | tee -a "$WATCH_LOG"

BASE_ROUTES=$(docker exec mysql8 mysql -uroot -p123456 -N -e \
  "SELECT COUNT(*) FROM pay_config.bill_route WHERE merchant_id BETWEEN 10001 AND 10100;" 2>/dev/null)
echo "$BASE_ROUTES" > "$RESULT_DIR/base-routes.txt"
echo "BASE_ROUTES=$BASE_ROUTES" | tee -a "$WATCH_LOG"

# watchdog in background
(
  while true; do
    if ! curl -sf http://127.0.0.1:18089/actuator/health >/dev/null 2>&1; then
      echo "$(date '+%F %T') watchdog restart" >>"$WATCH_LOG"
      start_app || true
    fi
    sleep 10
  done
) &
WATCH_PID=$!
echo "WATCH_PID=$WATCH_PID" | tee -a "$WATCH_LOG"

# run load test (blocking 10 min)
cd "$ROOT"
set +e
NAME_SERVER=127.0.0.1:9877 CLIENTS=5 TPS=30 DURATION=600 \
MERCHANT_ID_START=10001 MERCHANT_ID_END=10100 \
./pay-test/run-mq-load-test.sh --report-interval-seconds=60 \
  >"$LT_LOG" 2>&1
LT_EXIT=$?
set -e
echo "LOADTEST_EXIT=$LT_EXIT" | tee -a "$WATCH_LOG"
kill "$WATCH_PID" 2>/dev/null || true

# drain
echo "draining 45s..." | tee -a "$WATCH_LOG"
sleep 45

# capture results
cp "$LT_LOG" "$RESULT_DIR/mq-loadtest.log"
cp "$APP_LOG" "$RESULT_DIR/app.log" 2>/dev/null || true
docker exec rmq-broker sh -c 'cd /home/rocketmq/rocketmq-4.9.6 && sh bin/mqadmin consumerProgress -n rmq-namesrv:9876 2>/dev/null' \
  | head -12 >"$RESULT_DIR/mq-progress.txt" || true

END_ROUTES=$(docker exec mysql8 mysql -uroot -p123456 -N -e \
  "SELECT COUNT(*) FROM pay_config.bill_route WHERE merchant_id BETWEEN 10001 AND 10100;" 2>/dev/null)
echo "$END_ROUTES" >"$RESULT_DIR/end-routes.txt"
echo "END_ROUTES=$END_ROUTES DELTA=$((END_ROUTES-BASE_ROUTES))" | tee -a "$WATCH_LOG"

# shard distribution
{
  echo "=== bill_route by shard ==="
  docker exec mysql8 mysql -uroot -p123456 -e "
SELECT (merchant_id % 16) AS shard_id, COUNT(*) AS cnt
FROM pay_config.bill_route
WHERE merchant_id BETWEEN 10001 AND 10100
GROUP BY (merchant_id % 16) ORDER BY shard_id;" 2>/dev/null

  echo "=== trade_bill physical ==="
  for db in 00 01 02 03; do
    for s in 0 1 2 3; do
      n=$(docker exec mysql8 mysql -uroot -p123456 -N -e \
        "SELECT COUNT(*) FROM pay_data_${db}.trade_bill_${s} WHERE merchant_id BETWEEN 10001 AND 10100;" 2>/dev/null)
      echo "pay_data_${db}.trade_bill_${s}=$n"
    done
  done

  echo "=== clearance SUCCESS ==="
  total=0
  for db in 00 01 02 03; do
    for s in 0 1 2 3; do
      n=$(docker exec mysql8 mysql -uroot -p123456 -N -e \
        "SELECT COUNT(*) FROM pay_data_${db}.clearance_task_${s} WHERE merchant_id BETWEEN 10001 AND 10100 AND status=2;" 2>/dev/null)
      echo "pay_data_${db}.clearance_task_${s}=$n"
      total=$((total+n))
    done
  done
  echo "clearance_SUCCESS_total=$total"

  echo "=== merchants with wait_balance>0 ==="
  credited=0
  for db in 00 01 02 03; do
    for s in 0 1 2 3; do
      n=$(docker exec mysql8 mysql -uroot -p123456 -N -e \
        "SELECT COUNT(*) FROM pay_data_${db}.merchant_settle_account_${s} WHERE merchant_id BETWEEN 10001 AND 10100 AND wait_balance>0;" 2>/dev/null)
      credited=$((credited+n))
    done
  done
  echo "merchants_credited=$credited"

  echo "=== distinct merchants in bill_route ==="
  docker exec mysql8 mysql -uroot -p123456 -N -e \
    "SELECT COUNT(DISTINCT merchant_id) FROM pay_config.bill_route WHERE merchant_id BETWEEN 10001 AND 10100;" 2>/dev/null
} | tee "$RESULT_DIR/verify.txt"

echo "=== done $(date '+%F %T') ===" | tee -a "$WATCH_LOG"
exit "$LT_EXIT"
