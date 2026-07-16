#!/usr/bin/env bash
# calc 层 10 分钟消费监控：MQ 积压/TPS + Prometheus 指标 + 日志采样
set -euo pipefail

ROOT="$(cd "$(dirname "$0")/.." && pwd)"
RESULT="${RESULT_DIR:-/tmp/calc-monitor-10min}"
APP_LOG="${APP_LOG:-/tmp/pay-app-calc-monitor.log}"
JAR="$ROOT/pay-app/target/pay-app-1.0.0-SNAPSHOT.jar"
DURATION_SEC="${DURATION_SEC:-600}"
INTERVAL_SEC="${INTERVAL_SEC:-30}"
PROM_URL="${PROM_URL:-http://127.0.0.1:18089/actuator/prometheus}"

mkdir -p "$RESULT"
: >"$RESULT/samples.tsv"
: >"$RESULT/metrics.tsv"
: >"$RESULT/watch.log"

log() { echo "$(date '+%F %T') $*" | tee -a "$RESULT/watch.log"; }

start_app() {
  pkill -9 -f 'pay-app-1.0.0-SNAPSHOT.jar' 2>/dev/null || true
  sleep 2
  : >"$APP_LOG"
  nohup java -Xms512m -Xmx1536m -XX:+ExitOnOutOfMemoryError \
    -jar "$JAR" \
    --spring.profiles.active=mysql,sharding,mq \
    --payment.redis.enabled=true \
    --pay.mq.backlog.circuit-enabled=false \
    --pay.loadtest.pause-jobs=true \
    --pay.db.init.enabled=false \
    >>"$APP_LOG" 2>&1 &
  log "app started pid=$!"
  for i in $(seq 1 90); do
    if curl -sf --max-time 2 http://127.0.0.1:18089/actuator/health >/dev/null 2>&1; then
      log "health UP"
      return 0
    fi
    sleep 2
  done
  log "health FAIL"
  tail -60 "$APP_LOG" | tee -a "$RESULT/watch.log"
  return 1
}

mq_lag() {
  local prog
  prog=$(docker exec rmq-broker sh -c 'cd /home/rocketmq/rocketmq-4.9.6 && sh bin/mqadmin consumerProgress -n rmq-namesrv:9876 2>/dev/null' || true)
  ACCESS_TPS=$(echo "$prog" | awk '/^pay-access-consumer[[:space:]]/{print $(NF-1); exit}')
  ACCESS_DIFF=$(echo "$prog" | awk '/^pay-access-consumer[[:space:]]/{print $NF; exit}')
  CALC_TPS=$(echo "$prog" | awk '/^pay-calc-consumer[[:space:]]/{print $(NF-1); exit}')
  CALC_DIFF=$(echo "$prog" | awk '/^pay-calc-consumer[[:space:]]/{print $NF; exit}')
  SETTLE_DIFF=$(echo "$prog" | awk '/^pay-settlement-consumer[[:space:]]/{print $NF; exit}')
}

prom_metric() {
  local name="$1"
  curl -sf --max-time 3 "$PROM_URL" 2>/dev/null | awk -v n="$name" '
    $0 ~ "^" n "{|^" n " " {
      if ($0 ~ /^[^#]/ && $2 ~ /^[0-9]/) { print $2; exit }
    }
    $0 ~ "^" n " " && $2 ~ /^[0-9]/ { print $2; exit }
  ' || echo ""
}

prom_sum_counter() {
  local prefix="$1"
  curl -sf --max-time 3 "$PROM_URL" 2>/dev/null | awk -v p="$prefix" '
    $0 ~ "^" p && $0 !~ /^#/ { sum += $2 }
    END { if (sum > 0) printf "%.0f", sum; else print "" }
  ' || echo ""
}

prom_stage_p50_ms() {
  local stage="$1"
  curl -sf --max-time 3 "$PROM_URL" 2>/dev/null | awk -v s="$stage" '
    $0 ~ /^pay_calc_stage_duration_seconds_count\{.*stage="/ && index($0, "stage=\"" s "\"") {
      count = $2
    }
    $0 ~ /^pay_calc_stage_duration_seconds_sum\{.*stage="/ && index($0, "stage=\"" s "\"") {
      sum = $2
    }
    END {
      if (count > 0) printf "%.1f", (sum / count) * 1000
      else print ""
    }
  ' || echo ""
}

sample() {
  local tag="$1"
  mq_lag
  local health=UP
  curl -sf --max-time 2 http://127.0.0.1:18089/actuator/health >/dev/null 2>&1 || health=DOWN

  local calc_success calc_fail calc_skip consume_count pending running failed dead
  local hikari_active hikari_pending hikari_max
  local claim_ms fee_ms fin_ms

  calc_success=$(prom_sum_counter "pay_calc_clearance_total{.*status=\"success\"")
  calc_fail=$(prom_sum_counter "pay_calc_clearance_total{.*status=\"fail\"")
  calc_skip=$(prom_sum_counter "pay_calc_clearance_total{.*status=\"skip\"")
  consume_count=$(prom_metric "pay_calc_consume_duration_seconds_count")
  pending=$(prom_metric "pay_clearance_pending")
  running=$(prom_metric "pay_clearance_running")
  failed=$(prom_metric "pay_clearance_failed")
  dead=$(prom_metric "pay_clearance_dead")
  hikari_active=$(prom_sum_counter "hikaricp_connections_active")
  hikari_pending=$(prom_sum_counter "hikaricp_connections_pending")
  hikari_max=$(prom_sum_counter "hikaricp_connections_max")
  claim_ms=$(prom_stage_p50_ms "claim")
  fee_ms=$(prom_stage_p50_ms "fee_split")
  fin_ms=$(prom_stage_p50_ms "finalize")

  printf '%s\t%s\tcalcTps=%s\tcalcDiff=%s\taccessDiff=%s\tsettleDiff=%s\thealth=%s\n' \
    "$(date '+%F %T')" "$tag" "${CALC_TPS:-?}" "${CALC_DIFF:-?}" "${ACCESS_DIFF:-?}" "${SETTLE_DIFF:-?}" "$health" \
    | tee -a "$RESULT/samples.tsv"

  printf '%s\t%s\tsuccess=%s\tfail=%s\tskip=%s\tconsumeN=%s\tpending=%s\trunning=%s\tfailed=%s\tdead=%s\thikari=%s/%s\tpendingConn=%s\tclaimMs=%s\tfeeMs=%s\tfinMs=%s\n' \
    "$(date '+%F %T')" "$tag" "${calc_success:-?}" "${calc_fail:-?}" "${calc_skip:-?}" "${consume_count:-?}" \
    "${pending:-?}" "${running:-?}" "${failed:-?}" "${dead:-?}" \
    "${hikari_active:-?}" "${hikari_max:-?}" "${hikari_pending:-?}" \
    "${claim_ms:-?}" "${fee_ms:-?}" "${fin_ms:-?}" \
    | tee -a "$RESULT/metrics.tsv"

  log "SAMPLE[$tag] calcTps=${CALC_TPS:-?} calcDiff=${CALC_DIFF:-?} success=${calc_success:-?} hikari=${hikari_active:-?}/${hikari_max:-?} pendingConn=${hikari_pending:-?}"
}

log "=== calc 10min monitor start duration=${DURATION_SEC}s interval=${INTERVAL_SEC}s ==="
start_app
sample start

start_ts=$(date +%s)
while true; do
  elapsed=$(( $(date +%s) - start_ts ))
  if [[ $elapsed -ge $DURATION_SEC ]]; then
    break
  fi
  sleep "$INTERVAL_SEC"
  sample "t${elapsed}s"
done

sample end
docker exec rmq-broker sh -c 'cd /home/rocketmq/rocketmq-4.9.6 && sh bin/mqadmin consumerProgress -n rmq-namesrv:9876 2>/dev/null' \
  | head -12 | tee "$RESULT/mq-end.txt"
grep -E 'calc backlog snapshot|clearance failed|Connection is not available|HikariPool|pay_calc' "$APP_LOG" 2>/dev/null | tail -80 | tee "$RESULT/app-tail.log" || true
log "=== DONE samples=$(wc -l <"$RESULT/samples.tsv") ==="
