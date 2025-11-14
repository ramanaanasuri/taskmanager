#!/usr/bin/env bash
# scripts/db_apply.sh
# Apply a .sql file to the running MariaDB container using credentials from .env

set -euo pipefail

# -------------------------
# 1) Load .env automatically
# -------------------------
PROJECT_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"

ENV_FILE="$PROJECT_ROOT/.env"

if [[ ! -f "$ENV_FILE" ]]; then
  echo "❌ .env file not found at: $ENV_FILE"
  exit 1
fi

echo "🔧 Loading environment variables from .env ..."
set -a
. "$ENV_FILE"
set +a

# -------------------------
# 2) Validate env variables
# -------------------------
DB_NAME="${DB_NAME:-taskmanager}"
DB_ROOT_PASSWORD="${DB_ROOT_PASSWORD:-}"

if [[ -z "$DB_ROOT_PASSWORD" ]]; then
  echo "❌ ERROR: DB_ROOT_PASSWORD missing in .env"
  exit 2
fi

# -------------------------
# 3) Input validation
# -------------------------
SQL_FILE="${1:-}"

if [[ -z "$SQL_FILE" ]]; then
  echo "❌ Usage: $0 path/to/file.sql"
  exit 2
fi

if [[ ! -r "$SQL_FILE" ]]; then
  echo "❌ Cannot read SQL file: $SQL_FILE"
  exit 2
fi

# -------------------------
# 4) Detect DB container
# -------------------------
DB_CID="$(docker ps -qf name=taskmanager-db || true)"

if [[ -z "$DB_CID" ]]; then
  echo "❌ No DB container found with name 'taskmanager-db'"
  echo "   You can override by setting DB_CID=<container_id>"
  exit 2
fi

echo "🐳 Using DB container: $DB_CID"
echo "🗄   Database name:    $DB_NAME"
echo "📄 SQL file:          $SQL_FILE"

# -------------------------
# 5) Confirm (unless NO_CONFIRM)
# -------------------------
if [[ "${NO_CONFIRM:-}" != "1" ]]; then
  read -rp "⚠️  Type APPLY to execute SQL: " ACK
  if [[ "$ACK" != "APPLY" ]]; then
    echo "❎ Cancelled"
    exit 0
  fi
fi

# -------------------------
# 6) Choose DB client
# -------------------------
DB_CLIENT="mariadb"
if ! docker exec "$DB_CID" sh -lc "command -v mariadb >/dev/null"; then
  DB_CLIENT="mysql"
fi

# -------------------------
# 7) Run SQL
# -------------------------
echo "▶️  Applying SQL using $DB_CLIENT ..."
docker exec -i "$DB_CID" $DB_CLIENT -u root "-p$DB_ROOT_PASSWORD" "$DB_NAME" < "$SQL_FILE"

echo "✅ SQL applied successfully."

