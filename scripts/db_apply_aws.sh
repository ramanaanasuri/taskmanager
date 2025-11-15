#!/usr/bin/env bash
# scripts/db_apply.sh
# Apply a .sql file to the running MariaDB container using credentials from .env

set -euo pipefail

# -------------------------
# 1) Load .env automatically
# -------------------------
PROJECT_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
ENV_FILE="$PROJECT_ROOT/.env"

if [[ -f "$ENV_FILE" ]]; then
  echo "🔧 Loading environment variables from .env ..."
  set -a
  . "$ENV_FILE"
  set +a
else
  echo "⚠️  .env file not found at: $ENV_FILE"
  echo "   Relying on already-exported environment variables."
fi

# -------------------------
# 2) Validate env variables
# -------------------------
DB_NAME="${DB_NAME:-taskmanager}"
DB_ROOT_PASSWORD="${DB_ROOT_PASSWORD:-}"

if [[ -z "$DB_ROOT_PASSWORD" ]]; then
  echo "❌ ERROR: DB_ROOT_PASSWORD missing (in .env or environment)."
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

# Make SQL_FILE absolute so redirection works no matter where we run from
if [[ "$SQL_FILE" != /* ]]; then
  SQL_FILE="$PROJECT_ROOT/$SQL_FILE"
fi

# -------------------------
# 4) Detect DB container/service (AWS & GCP safe)
# -------------------------
DB_CID="${DB_CID:-taskmanager-db}"

# Sanity check: ensure it exists as a running container or compose service
if ! docker ps --format '{{.Names}}' | grep -qx "$DB_CID"; then
  echo "❌ No running container/service named '$DB_CID'."
  echo "   Running containers are:"
  docker ps --format '  {{.Names}}'
  exit 1
fi

echo "🐳 Using DB container/service: $DB_CID"
echo "🗄   Database name:            $DB_NAME"
echo "📄 SQL file:                  $SQL_FILE"

# -------------------------
# 5) Confirm (unless NO_CONFIRM)
# -------------------------
if [[ "${NO_CONFIRM:-}" != "1" ]]; then
  read -rp "⚠️  Type APPLY to execute SQL: " ACK
  if [[ "$ACK" != "APPLY" ]]; then
    echo "❎ Cancelled."
    exit 0
  fi
fi

# -------------------------
# 6) Choose DB client
# -------------------------
DB_CLIENT="mariadb"
if ! docker exec "$DB_CID" sh -lc "command -v mariadb >/dev/null 2>&1"; then
  DB_CLIENT="mysql"
fi

# -------------------------
# 7) Run SQL
# -------------------------
echo "▶️  Applying SQL using $DB_CLIENT ..."
docker exec -i "$DB_CID" $DB_CLIENT -u root "-p$DB_ROOT_PASSWORD" "$DB_NAME" < "$SQL_FILE"

echo "✅ SQL applied successfully."

