#!/usr/bin/env bash
# 手动初始化 MySQL 数据库（建库 + 建表 + 种子数据）
# 用法: ./scripts/init-db.sh
# 依赖: mysql 客户端已在 PATH 中

set -euo pipefail

ROOT_DIR="$(cd "$(dirname "$0")/.." && pwd)"
DB_HOST="${DB_HOST:-127.0.0.1}"
DB_PORT="${DB_PORT:-3306}"
DB_NAME="${DB_NAME:-pay_settle}"
DB_USER="${DB_USER:-root}"
DB_PASS="${DB_PASS:-123456}"

MYSQL=(mysql -h"$DB_HOST" -P"$DB_PORT" -u"$DB_USER" -p"$DB_PASS")

echo ">>> 创建数据库 $DB_NAME ..."
"${MYSQL[@]}" < "$ROOT_DIR/scripts/init-mysql.sql"

echo ">>> 执行建表脚本 schema.sql ..."
"${MYSQL[@]}" "$DB_NAME" < "$ROOT_DIR/pay-app/src/main/resources/db/schema.sql"

echo ">>> 执行种子数据 data.sql ..."
"${MYSQL[@]}" "$DB_NAME" < "$ROOT_DIR/pay-app/src/main/resources/db/data-mysql.sql"

echo ">>> 初始化完成: $DB_NAME @ $DB_HOST:$DB_PORT"
