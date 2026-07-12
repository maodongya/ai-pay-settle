#!/usr/bin/env bash
# 一键启动 RocketMQ（NameServer + Broker + Dashboard）并预创建 Topic
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "$0")" && pwd)"
COMPOSE_FILE="$ROOT_DIR/docker-compose.yml"
BROKER_TEMPLATE="$ROOT_DIR/broker.conf"
BROKER_RUNTIME="$ROOT_DIR/broker-runtime.conf"
NAMESRV_ADDR="${NAMESRV_ADDR:-namesrv:9876}"
CLUSTER="${CLUSTER:-DefaultCluster}"

TOPICS=(
  trade_pay_topic
  trade_refund_topic
  clearance_task_topic
  settle_amount_topic
  payment_result_topic
)

detect_host_ip() {
  local ip=""
  ip=$(ipconfig getifaddr en0 2>/dev/null || true)
  if [[ -z "$ip" ]]; then
    ip=$(ipconfig getifaddr en1 2>/dev/null || true)
  fi
  if [[ -z "$ip" ]]; then
    ip=$(ifconfig | awk '/inet / && $2 != "127.0.0.1" {print $2; exit}')
  fi
  if [[ -z "$ip" ]]; then
    echo "无法自动检测本机 IP，请手动编辑 pay-mq/broker-runtime.conf 中的 brokerIP1" >&2
    ip="127.0.0.1"
  fi
  echo "$ip"
}

HOST_IP="${BROKER_IP1:-$(detect_host_ip)}"
echo ">>> 使用 brokerIP1=$HOST_IP（Dashboard/客户端通过此地址访问 Broker）"
sed "s/^brokerIP1=.*/brokerIP1=$HOST_IP/" "$BROKER_TEMPLATE" > "$BROKER_RUNTIME"

echo ">>> 清理旧容器（若存在）..."
docker rm -f rmq-dashboard rmq-broker rmq-namesrv 2>/dev/null || true

echo ">>> 启动 RocketMQ 容器..."
docker compose -f "$COMPOSE_FILE" up -d

echo ">>> 等待 Broker 就绪..."
for i in $(seq 1 40); do
  if docker logs rmq-broker 2>&1 | grep -q "boot success"; then
    break
  fi
  sleep 1
done
docker logs rmq-broker 2>&1 | tail -3

MQADMIN="/home/rocketmq/rocketmq-4.9.6/bin/mqadmin"
echo ">>> 预创建 Topic ..."
for topic in "${TOPICS[@]}"; do
  docker exec rmq-broker sh -c \
    "$MQADMIN updateTopic -n $NAMESRV_ADDR -t $topic -c $CLUSTER" \
    && echo "  created: $topic" \
    || echo "  warn: $topic may already exist"
done

echo ""
echo ">>> 完成"
echo "  NameServer : 127.0.0.1:9877"
echo "  Dashboard  : http://127.0.0.1:8081"
echo "  brokerIP1  : $HOST_IP"
echo ""
echo "  pay-app 启动: --spring.profiles.active=h2,mq"
echo "  压测命令    : --name-server=127.0.0.1:9877"
