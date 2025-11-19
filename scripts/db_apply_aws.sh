#!/usr/bin/env bash
# scripts/db_apply.sh
# Apply a .sql file to the running MariaDB container using credentials from .env
# AWS-compatible version - copies file into container to avoid TTY/stdin issues

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
# 4) Detect DB container (AWS-compatible)
# -------------------------
DB_CONTAINER="${DB_CONTAINER:-taskmanager-db}"

if ! docker ps --format '{{.Names}}' | grep -qx "$DB_CONTAINER"; then
  echo "❌ Could not find a running DB container named '$DB_CONTAINER'."
  echo "   Current containers:"
  docker ps --format 'table {{.ID}}\t{{.Names}}\t{{.Status}}'
  echo "   If your DB container has a different name, run:"
  echo "     DB_CONTAINER=<n> $0 $SQL_FILE"
  exit 2
fi

echo "🐳 Using DB container: $DB_CONTAINER"
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
# 6) Apply SQL by copying file into container (avoids stdin/TTY)
# -------------------------
echo "▶️  Applying SQL ..."

# Use unique temp filename
TMP_FILE="/tmp/sql_apply_$$.sql"

# Step 1: Copy SQL file into container
docker cp "$SQL_FILE" "$DB_CONTAINER:$TMP_FILE"

# Step 2: Execute SQL inside container (redirection happens inside container, no TTY issues)
docker exec "$DB_CONTAINER" sh -c "mariadb -uroot -p${DB_ROOT_PASSWORD} ${DB_NAME} < ${TMP_FILE}"

# Step 3: Cleanup
docker exec "$DB_CONTAINER" rm -f "$TMP_FILE"

echo "✅ SQL applied successfully."
