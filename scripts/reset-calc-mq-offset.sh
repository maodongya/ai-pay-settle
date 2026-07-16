#!/usr/bin/env bash
# PR-C2：drain/压测前重置 calc 相关 MQ 消费位点，避免历史脏消息空转。
set -euo pipefail

NS="${NAME_SERVER:-rmq-namesrv:9876}"
BROKER_CONTAINER="${BROKER_CONTAINER:-rmq-broker}"
MQADMIN_HOME="${MQADMIN_HOME:-/home/rocketmq/rocketmq-4.9.6}"

run_mqadmin() {
  docker exec "$BROKER_CONTAINER" sh -c "cd $MQADMIN_HOME && sh bin/mqadmin $* -n $NS"
}

echo "== reset calc/access MQ offsets to latest =="
for pair in \
  "pay-calc-consumer:clearance_task_topic" \
  "pay-calc-consumer:%RETRY%pay-calc-consumer" \
  "pay-access-consumer:trade_pay_topic" \
  "pay-access-consumer:%RETRY%pay-access-consumer" \
  "pay-settlement-consumer:settle_amount_topic" \
  "pay-settlement-consumer-payment:settle_payment_topic"
do
  g=${pair%%:*}
  t=${pair#*:}
  echo "reset group=$g topic=$t"
  run_mqadmin resetOffsetByTime -g "$g" -t "$t" -s now -f true || true
done

echo "== consumer progress =="
run_mqadmin consumerProgress | head -12
