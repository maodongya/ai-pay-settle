#!/usr/bin/env bash
# MQ trade_pay_topic 压测（可用环境变量覆盖 TPS/时长/商户区间）
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "$0")/.." && pwd)"
JAR="$ROOT_DIR/pay-test/target/pay-test-1.0.0-SNAPSHOT-jar-with-dependencies.jar"
NAME_SERVER="${NAME_SERVER:-127.0.0.1:9877}"
CLIENTS="${CLIENTS:-10}"
TPS="${TPS:-1000}"
DURATION="${DURATION:-1200}"
EXTRA_ARGS=()

if [[ -n "${MERCHANT_ID_START:-}" && -n "${MERCHANT_ID_END:-}" ]]; then
  EXTRA_ARGS+=(--merchant-id-start="$MERCHANT_ID_START" --merchant-id-end="$MERCHANT_ID_END")
elif [[ -n "${MERCHANT_ID:-}" ]]; then
  EXTRA_ARGS+=(--merchant-id="$MERCHANT_ID")
fi

if [[ ! -f "$JAR" ]]; then
  echo ">>> 构建 pay-test ..."
  mvn -f "$ROOT_DIR/pom.xml" -pl pay-test -am package -DskipTests -q
fi

echo ">>> MQ 压测: topic=trade_pay_topic, nameServer=$NAME_SERVER, clients=$CLIENTS, tps=$TPS, duration=${DURATION}s ${EXTRA_ARGS[*]:-}"
exec java -cp "$JAR" com.payment.test.mq.MqLoadTestMain \
  --name-server="$NAME_SERVER" \
  --clients="$CLIENTS" \
  --tps="$TPS" \
  --duration="$DURATION" \
  "${EXTRA_ARGS[@]}" \
  "$@"
