#!/usr/bin/env bash
# C2：将 Consumer Group 消费位点重置到当前时间，丢掉历史脏积压。
# 仅用于压测 / 运维窗口；生产需审批并确认无合法未消费消息。
set -euo pipefail

BROKER_CONTAINER="${ROCKETMQ_BROKER_CONTAINER:-rmq-broker}"
NAMESRV="${ROCKETMQ_NAMESRV:-rmq-namesrv:9876}"
ROCKETMQ_HOME="${ROCKETMQ_HOME:-/home/rocketmq/rocketmq-4.9.6}"

echo ">>> reset MQ offsets to now (container=$BROKER_CONTAINER namesrv=$NAMESRV)"
docker exec "$BROKER_CONTAINER" sh -c "
cd $ROCKETMQ_HOME
for pair in \
  'pay-access-consumer:trade_pay_topic' \
  'pay-access-consumer:%RETRY%pay-access-consumer' \
  'pay-calc-consumer:clearance_task_topic' \
  'pay-calc-consumer:%RETRY%pay-calc-consumer' \
  'pay-settlement-consumer:settle_amount_topic'
do
  g=\${pair%%:*}; t=\${pair#*:}
  sh bin/mqadmin resetOffsetByTime -n $NAMESRV -g \"\$g\" -t \"\$t\" -s now -f true || true
done
" || echo "mq reset skipped/failed"
echo ">>> reset done"
