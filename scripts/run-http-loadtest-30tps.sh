#!/usr/bin/env bash
# HTTP 清算压测：默认 30 TPS × 1800s（商户 1000001~1001000）
set -euo pipefail
BASE_URL="${BASE_URL:-http://127.0.0.1:18089}"
TPS="${TPS:-30}"
DURATION="${DURATION:-1800}"
MERCHANT_START="${MERCHANT_ID_START:-1000001}"
MERCHANT_END="${MERCHANT_ID_END:-1001000}"
RESULT_DIR="${RESULT_DIR:-/tmp/http-loadtest-redis-result}"
LOG="${LOG:-/tmp/http-loadtest-redis.log}"
mkdir -p "$RESULT_DIR"

INTERVAL=$(python3 - <<PY
print(1.0 / max(1, int("${TPS}")))
PY
)
TOTAL=$((TPS * DURATION))
RANGE=$((MERCHANT_END - MERCHANT_START + 1))
TS=$(date +%s)
OK=0
FAIL=0
START_EPOCH=$(date +%s)

echo "=== HTTP loadtest $(date '+%F %T') tps=$TPS duration=${DURATION}s total=$TOTAL merchants=${MERCHANT_START}-${MERCHANT_END} ===" | tee "$LOG"

BASE_ROUTES=$(docker exec mysql8 mysql -uroot -p123456 -N -e \
  "SELECT COUNT(*) FROM pay_config.bill_route WHERE merchant_id BETWEEN $MERCHANT_START AND $MERCHANT_END;" 2>/dev/null || echo 0)
echo "BASE_ROUTES=$BASE_ROUTES" | tee -a "$LOG"

# 10 worker pool via background jobs + rate limiter in parent
next=1
deadline=$((START_EPOCH + DURATION + 30))
while (( next <= TOTAL )); do
  now=$(date +%s)
  if (( now >= START_EPOCH + DURATION )); then
    break
  fi
  # pace: target next send time
  elapsed_ms=$(( ($(date +%s%N) / 1000000) - START_EPOCH * 1000 ))
  expected=$(( next - 1 ))
  target_ms=$(( expected * 1000 / TPS ))
  if (( elapsed_ms < target_ms )); then
    sleep_ms=$(( target_ms - elapsed_ms ))
    python3 - <<PY
import time
time.sleep(${sleep_ms}/1000.0)
PY
  fi

  # limit in-flight concurrency
  while (( $(jobs -rp | wc -l) >= 20 )); do
    wait -n 2>/dev/null || sleep 0.05
  done

  i=$next
  (
    B="HT${TS}${i}"
    MID=$((MERCHANT_START + (i % RANGE)))
    CODE=$(curl -s -o /dev/null -w '%{http_code}' --max-time 30 -X POST "$BASE_URL/api/v1/clearance/bill/submit" \
      -H 'Content-Type: application/json' \
      -d "{\"billNo\":\"$B\",\"billType\":1,\"businessLine\":\"A\",\"category\":\"A01\",\"serviceItem\":\"A0101\",\"merchantId\":$MID,\"agentId\":20001,\"secondAgentId\":20002,\"orderNo\":\"ORD$B\",\"tradeAmount\":50.00,\"cityCode\":\"110000\",\"payChannel\":\"ALIPAY\"}" || echo 000)
    echo "$CODE" >>"$RESULT_DIR/codes.txt"
  ) &
  next=$((next + 1))

  if (( next % (TPS * 60) == 1 )); then
    okc=$(grep -c '^200$' "$RESULT_DIR/codes.txt" 2>/dev/null || echo 0)
    allc=$(wc -l <"$RESULT_DIR/codes.txt" 2>/dev/null || echo 0)
    routes=$(docker exec mysql8 mysql -uroot -p123456 -N -e \
      "SELECT COUNT(*) FROM pay_config.bill_route WHERE merchant_id BETWEEN $MERCHANT_START AND $MERCHANT_END;" 2>/dev/null || echo -1)
    rsize=$(docker exec redis7 redis-cli DBSIZE 2>/dev/null || echo -1)
    health=UP
    curl -sf "$BASE_URL/actuator/health" >/dev/null || health=DOWN
    echo "$(date '+%F %T') progress sent=$((next-1)) http200=$okc total_resp=$allc routes=$routes redisKeys=$rsize health=$health" | tee -a "$LOG"
  fi
done
wait

END_EPOCH=$(date +%s)
OK=$(grep -c '^200$' "$RESULT_DIR/codes.txt" 2>/dev/null || echo 0)
FAIL=$(grep -cv '^200$' "$RESULT_DIR/codes.txt" 2>/dev/null || echo 0)
ALL=$(wc -l <"$RESULT_DIR/codes.txt" 2>/dev/null || echo 0)
END_ROUTES=$(docker exec mysql8 mysql -uroot -p123456 -N -e \
  "SELECT COUNT(*) FROM pay_config.bill_route WHERE merchant_id BETWEEN $MERCHANT_START AND $MERCHANT_END;" 2>/dev/null || echo 0)

{
  echo "elapsed=$((END_EPOCH-START_EPOCH))s"
  echo "sent_target=$TOTAL responses=$ALL http200=$OK non200=$FAIL"
  echo "BASE_ROUTES=$BASE_ROUTES END_ROUTES=$END_ROUTES DELTA=$((END_ROUTES-BASE_ROUTES))"
  echo "redis_dbsize=$(docker exec redis7 redis-cli DBSIZE 2>/dev/null || echo -1)"
  echo "feeRules=$(docker exec redis7 redis-cli EXISTS feeRules::all 2>/dev/null || echo -1)"
  echo "=== http code hist ==="
  sort "$RESULT_DIR/codes.txt" 2>/dev/null | uniq -c | sort -nr
} | tee "$RESULT_DIR/summary.txt" | tee -a "$LOG"

echo "=== done $(date '+%F %T') ===" | tee -a "$LOG"
