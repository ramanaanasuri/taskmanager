#!/usr/bin/env bash
# scripts/db_apply_aws.sh
# Apply a .sql file to the running MariaDB DB (AWS + GCP compatible)

set -euo pipefail

# -------------------------
# 0) Project root & .env
# -------------------------
PROJECT_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$PROJECT_ROOT" || exit 1

ENV_FILE="$PROJECT_ROOT/.env"

if [[ -f "$ENV_FILE" ]]; then
  echo "🔧 Loading environment from .env ..."
  set -a
  . "$ENV_FILE"
  set +a
else
  echo "⚠️  .env not found at $ENV_FILE — relying on environment variables only."
fi

# -------------------------
# 1) Config
# -------------------------
DB_NAME="${DB_NAME:-taskmanager}"
DB_ROOT_PASSWORD="${DB_ROOT_PASSWORD:-}"
DB_SERVICE="${DB_CID:-taskmanager-db}"

if [[ -z "$DB_ROOT_PASSWORD" ]]; then
  echo "❌ DB_ROOT_PASSWORD is not set (check .env)."
  exit 1
fi

# -------------------------
# 2) SQL file arg
# -------------------------
SQL_FILE="${1:-}"

if [[ -z "$SQL_FILE" ]]; then
  echo "❌ Usage: $0 path/to/migration.sql"
  exit 1
fi

# Normalize to absolute path
if [[ "$SQL_FILE" != /* ]]; then
  SQL_FILE="$PROJECT_ROOT/$SQL_FILE"
fi

if [[ ! -r "$SQL_FILE" ]]; then
  echo "❌ Cannot read SQL file: $SQL_FILE"
  exit 1
fi

# -------------------------
# 3) Detect docker compose vs docker
# -------------------------
COMPOSE_CMD=""
if command -v docker &>/dev/null && docker compose version &>/dev/null; then
  COMPOSE_CMD="docker compose"
elif command -v docker-compose &>/dev/null; then
  COMPOSE_CMD="docker-compose"
fi

# Verify DB service/container exists
if [[ -n "$COMPOSE_CMD" ]]; then
  if ! $COMPOSE_CMD ps --services | grep -qx "$DB_SERVICE"; then
    echo "❌ No compose service named '$DB_SERVICE'."
    echo "   Services:"
    $COMPOSE_CMD ps --services
    exit 1
  fi
else
  if ! docker ps --format '{{.Names}}' | grep -qx "$DB_SERVICE"; then
    echo "❌ No running container named '$DB_SERVICE'."
    echo "   Running containers:"
    docker ps --format '  {{.Names}}'
    exit 1
  fi
fi

# -------------------------
# 4) Confirm
# -------------------------
echo "🗄  Database:        $DB_NAME"
echo "🐳 DB service:       $DB_SERVICE"
echo "📄 SQL file:         $SQL_FILE"
read -rp "Type 'APPLY' to run this migration: " CONFIRM
if [[ "$CONFIRM" != "APPLY" ]]; then
  echo "❎ Cancelled."
  exit 0
fi

# -------------------------
# 5) Apply SQL (no TTY)
# -------------------------
echo "▶️  Applying SQL ..."

if [[ -n "$COMPOSE_CMD" ]]; then
  # Use -T to avoid “input device is not a TTY”
  $COMPOSE_CMD exec -T "$DB_SERVICE" \
    mariadb -uroot -p"$DB_ROOT_PASSWORD" "$DB_NAME" \
    < "$SQL_FILE"
else
  docker exec -i "$DB_SERVICE" \
    mariadb -uroot -p"$DB_ROOT_PASSWORD" "$DB_NAME" \
    < "$SQL_FILE"
fi

echo "✅ SQL applied successfully."

