#!/usr/bin/env bash
# 启动 Grafana + Prometheus 监控栈（独立数据卷，与业务库隔离）
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "$0")" && pwd)"

echo ">>> 启动监控栈..."
docker compose -f "$ROOT_DIR/docker-compose.yml" up -d

echo ""
echo ">>> 完成"
echo "  Grafana    : http://127.0.0.1:3000  (admin / admin)"
echo "  Prometheus : http://127.0.0.1:9090"
echo ""
echo "  请确保 pay-app 已启动并暴露指标："
echo "    mvn -pl pay-app spring-boot:run -Dspring-boot.run.profiles=mysql,mq"
echo "    curl -s http://127.0.0.1:18089/actuator/prometheus | grep pay_"
echo ""
echo "  Prometheus 通过 host.docker.internal:18089 抓取应用指标"
