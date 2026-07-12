#!/usr/bin/env bash
# MQ trade_pay_topic 压测：10 客户端，1000 TPS，20 分钟
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "$0")/.." && pwd)"
JAR="$ROOT_DIR/pay-test/target/pay-test-1.0.0-SNAPSHOT-jar-with-dependencies.jar"
NAME_SERVER="${NAME_SERVER:-127.0.0.1:9877}"
CLIENTS="${CLIENTS:-10}"
TPS="${TPS:-1000}"
DURATION="${DURATION:-1200}"

if [[ ! -f "$JAR" ]]; then
  echo ">>> 构建 pay-test ..."
  mvn -f "$ROOT_DIR/pom.xml" -pl pay-test -am package -DskipTests -q
fi

echo ">>> MQ 压测: topic=trade_pay_topic, nameServer=$NAME_SERVER"
exec java -cp "$JAR" com.payment.test.mq.MqLoadTestMain \
  --name-server="$NAME_SERVER" \
  --clients="$CLIENTS" \
  --tps="$TPS" \
  --duration="$DURATION" \
  "$@"
