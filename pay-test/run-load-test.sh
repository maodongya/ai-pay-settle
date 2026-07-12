#!/usr/bin/env bash
# 清算接口压测：1000 客户端，TPS 2000
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "$0")/.." && pwd)"
JAR="$ROOT_DIR/pay-test/target/pay-test-1.0.0-SNAPSHOT-jar-with-dependencies.jar"
BASE_URL="${BASE_URL:-http://127.0.0.1:18089}"
CLIENTS="${CLIENTS:-1000}"
TPS="${TPS:-2000}"
DURATION="${DURATION:-60}"

if [[ ! -f "$JAR" ]]; then
  echo ">>> 构建 pay-test ..."
  mvn -f "$ROOT_DIR/pom.xml" -pl pay-test -am package -DskipTests -q
fi

echo ">>> 压测目标: $BASE_URL/api/v1/clearance/bill/submit"
java -jar "$JAR" \
  --url="$BASE_URL" \
  --clients="$CLIENTS" \
  --tps="$TPS" \
  --duration="$DURATION" \
  "$@"
