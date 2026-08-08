#!/usr/bin/env bash
# C1 回归压测：30 TPS × 5 分钟，关注 finalize No value present 与清算 SUCCESS
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
APP_JAR="$ROOT/pay-app/target/pay-app-1.0.0-SNAPSHOT.jar"
RESULT_DIR="${RESULT_DIR:-/tmp/c1-loadtest-result}"
APP_LOG="$RESULT_DIR/app.log"
LT_LOG="$RESULT_DIR/mq-loadtest.log"
WATCH_LOG="$RESULT_DIR/watch.log"
DURATION="${DURATION:-300}"
TPS="${TPS:-30}"
mkdir -p "$RESULT_DIR"
: >"$WATCH_LOG"

log() { echo "$(date '+%F %T') $*" | tee -a "$WATCH_LOG"; }

start_app() {
  if curl -sf --max-time 2 http://127.0.0.1:18089/actuator/health >/dev/null 2>&1; then
    log "app already healthy"
    return 0
  fi
  pkill -9 -f 'pay-app-1.0.0-SNAPSHOT.jar' 2>/dev/null || true
  sleep 2
  : >"$APP_LOG"
  nohup java -Xms512m -Xmx1536m -XX:+ExitOnOutOfMemoryError \
    -jar "$APP_JAR" \
    --spring.profiles.active=mysql,sharding,mq \
    --payment.redis.enabled=true \
    --pay.mq.backlog.circuit-enabled=false \
    --pay.loadtest.pause-jobs=true \
    --pay.db.init.enabled=false \
    >>"$APP_LOG" 2>&1 &
  log "started app pid=$!"
  for i in $(seq 1 90); do
    if curl -sf --max-time 2 http://127.0.0.1:18089/actuator/health >/dev/null 2>&1; then
      log "health UP"
      return 0
    fi
    sleep 2
  done
  log "health FAIL"
  tail -80 "$APP_LOG" | tee -a "$WATCH_LOG"
  return 1
}

log "=== C1 loadtest prepare ==="

pkill -f 'com.payment.test.mq.MqLoadTestMain' 2>/dev/null || true
sleep 1

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
' || log "mq reset skipped/failed"

start_app

# baseline SUCCESS count
BASE_SUCCESS=0
for db in 00 01 02 03; do
  for s in 0 1 2 3; do
    n=$(docker exec mysql8 mysql -uroot -p123456 -N -e \
      "SELECT COUNT(*) FROM pay_data_${db}.clearance_task_${s} WHERE merchant_id BETWEEN 10001 AND 10100 AND status=2;" 2>/dev/null || echo 0)
    BASE_SUCCESS=$((BASE_SUCCESS + n))
  done
done
echo "$BASE_SUCCESS" >"$RESULT_DIR/base-success.txt"
log "BASE_SUCCESS=$BASE_SUCCESS"

# baseline prometheus counters if available
curl -sf http://127.0.0.1:18089/actuator/prometheus >"$RESULT_DIR/prom-before.txt" || true

log "start producer TPS=$TPS DURATION=${DURATION}s"
cd "$ROOT"
set +e
NAME_SERVER=127.0.0.1:9877 CLIENTS=5 TPS="$TPS" DURATION="$DURATION" \
MERCHANT_ID_START=10001 MERCHANT_ID_END=10100 \
./pay-test/run-mq-load-test.sh --report-interval-seconds=60 \
  >"$LT_LOG" 2>&1
LT_EXIT=$?
set -e
log "LOADTEST_EXIT=$LT_EXIT"

log "draining 60s..."
sleep 60

curl -sf http://127.0.0.1:18089/actuator/prometheus >"$RESULT_DIR/prom-after.txt" || true
docker exec rmq-broker sh -c 'cd /home/rocketmq/rocketmq-4.9.6 && sh bin/mqadmin consumerProgress -n rmq-namesrv:9876 2>/dev/null' \
  | head -20 >"$RESULT_DIR/mq-progress.txt" || true

END_SUCCESS=0
for db in 00 01 02 03; do
  for s in 0 1 2 3; do
    n=$(docker exec mysql8 mysql -uroot -p123456 -N -e \
      "SELECT COUNT(*) FROM pay_data_${db}.clearance_task_${s} WHERE merchant_id BETWEEN 10001 AND 10100 AND status=2;" 2>/dev/null || echo 0)
    END_SUCCESS=$((END_SUCCESS + n))
  done
done
DELTA_SUCCESS=$((END_SUCCESS - BASE_SUCCESS))
echo "$END_SUCCESS" >"$RESULT_DIR/end-success.txt"
echo "$DELTA_SUCCESS" >"$RESULT_DIR/delta-success.txt"

# C1 error signatures
NO_VALUE=$(rg -c "No value present" "$APP_LOG" 2>/dev/null || echo 0)
FINALIZE_MISMATCH=$(rg -c "finalize (bill|task) status mismatch" "$APP_LOG" 2>/dev/null || echo 0)
CLEARANCE_FAIL=$(rg -c "clearance failed billNo=" "$APP_LOG" 2>/dev/null || echo 0)

{
  echo "DURATION_SEC=$DURATION"
  echo "TPS=$TPS"
  echo "EXPECTED_SEND_APPROX=$((TPS * DURATION))"
  echo "BASE_SUCCESS=$BASE_SUCCESS"
  echo "END_SUCCESS=$END_SUCCESS"
  echo "DELTA_SUCCESS=$DELTA_SUCCESS"
  echo "NO_VALUE_PRESENT_COUNT=$NO_VALUE"
  echo "FINALIZE_STATUS_MISMATCH_COUNT=$FINALIZE_MISMATCH"
  echo "CLEARANCE_FAILED_LOG_COUNT=$CLEARANCE_FAIL"
} | tee "$RESULT_DIR/summary.txt" | tee -a "$WATCH_LOG"

# extract producer sent lines
rg -n "sent=|totalSent|report|TPS|duration" "$LT_LOG" | tail -40 >"$RESULT_DIR/producer-tail.txt" || true

log "=== C1 loadtest done ==="
exit "$LT_EXIT"
