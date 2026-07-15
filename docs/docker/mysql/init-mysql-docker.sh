#!/usr/bin/env bash
# 部署/重启调优后的 MySQL8（保留已有数据目录）
set -euo pipefail
ROOT="$(cd "$(dirname "$0")" && pwd)"
DATA_DIR="${MYSQL_DATA_DIR:-/Users/maodongya/docker/mysql/data}"
CONF_DIR="${MYSQL_CONF_DIR:-/Users/maodongya/docker/mysql/conf}"

mkdir -p "$DATA_DIR" "$CONF_DIR"
cp -f "$ROOT/my.cnf" "$CONF_DIR/99-pay-settle.cnf"
echo ">>> installed $CONF_DIR/99-pay-settle.cnf"

if docker ps -a --format '{{.Names}}' | grep -qx mysql8; then
  echo ">>> recreate existing mysql8 with compose (preserve data volume)"
  docker rm -f mysql8 >/dev/null 2>&1 || true
fi

echo ">>> docker compose up (data=$DATA_DIR conf=$CONF_DIR)"
docker compose -f "$ROOT/docker-compose.yml" up -d

echo ">>> wait health"
for i in $(seq 1 40); do
  if docker exec mysql8 mysqladmin ping -uroot -p"${MYSQL_ROOT_PASSWORD:-123456}" --silent 2>/dev/null; then
    break
  fi
  sleep 2
done

echo ">>> verify"
docker exec mysql8 mysql -uroot -p"${MYSQL_ROOT_PASSWORD:-123456}" -e "
SHOW VARIABLES WHERE Variable_name IN ('max_connections','innodb_buffer_pool_size','wait_timeout');
SHOW STATUS LIKE 'Max_used_connections';
" 2>/dev/null | grep -v Warning

echo ">>> done"
