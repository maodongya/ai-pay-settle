#!/usr/bin/env bash
# 启动全方位监控栈：Prometheus + Grafana + 基础设施 Exporter
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "$0")" && pwd)"

echo ">>> 启动监控栈（Prometheus / Grafana / Exporters）..."
docker compose -f "$ROOT_DIR/docker-compose.yml" up -d

echo ""
echo ">>> 等待 Prometheus 就绪..."
for i in $(seq 1 30); do
  if curl -sf http://127.0.0.1:9090/-/ready >/dev/null 2>&1; then
    break
  fi
  sleep 1
done

echo ""
echo ">>> 抓取目标状态"
curl -sf http://127.0.0.1:9090/api/v1/targets 2>/dev/null \
  | python3 -c "
import json,sys
data=json.load(sys.stdin)
for t in data.get('data',{}).get('activeTargets',[]):
    print(f\"  {t.get('labels',{}).get('job','?'):20} {t.get('health','?'):6} {t.get('scrapeUrl','')}\")
" 2>/dev/null || echo "  (需安装 python3 或手动打开 http://127.0.0.1:9090/targets)"

echo ""
echo ">>> 完成"
echo "  Grafana         : http://127.0.0.1:3000  (admin / admin)"
echo "  Prometheus      : http://127.0.0.1:9090"
echo "  cAdvisor        : http://127.0.0.1:8082"
echo "  node-exporter   : http://127.0.0.1:9100/metrics"
echo "  mysqld-exporter : http://127.0.0.1:9104/metrics"
echo "  redis-exporter  : http://127.0.0.1:9121/metrics"
echo "  rocketmq-export : http://127.0.0.1:5557/metrics"
echo ""
echo "  Grafana 看板（Pay-Settle 文件夹）："
echo "    - 全方位基础设施（默认首页）"
echo "    - 应用运行时"
echo "    - Pay-Settle Overview"
echo ""
echo "  请确保以下服务已启动："
echo "    mysql8 :3306 | redis7 :6379 | rmq-namesrv :9877 | pay-app :18089"
echo ""
echo "  验证应用指标："
echo "    curl -s http://127.0.0.1:18089/actuator/prometheus | grep -E 'pay_mq_consumer_lag|process_cpu'"
