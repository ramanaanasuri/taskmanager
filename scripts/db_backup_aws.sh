#!/usr/bin/env bash
# scripts/db_backup_aws.sh
set -euo pipefail

# Go to project root (scripts/..)
cd "$(dirname "${BASH_SOURCE[0]}")/.." || exit 1

# Load .env if present
if [[ -f ".env" ]]; then
  echo "🔧 Loading environment from .env ..."
  set -a
  source .env
  set +a
else
  echo "⚠️  .env not found, relying on environment variables only"
fi

DB_NAME="${DB_NAME:-taskmanager}"
DB_ROOT_PASSWORD="${DB_ROOT_PASSWORD:-}"
BACKUP_DIR="${BACKUP_DIR:-db/backups}"

if [[ -z "$DB_ROOT_PASSWORD" ]]; then
  echo "❌ DB_ROOT_PASSWORD is not set (check .env)"
  exit 1
fi

mkdir -p "$BACKUP_DIR"

# IMPORTANT: use service/container name, not ID
DB_CID="${DB_CID:-taskmanager-db}"

TS="$(date -u +%Y%m%dT%H%M%SZ)"
OUT_FILE="${BACKUP_DIR}/${DB_NAME}_backup_${TS}.sql"

echo "📦 Backing up DB '${DB_NAME}' from container/service '${DB_CID}' ..."

# On EC2, `docker` is behaving like `docker compose` → this still works:
# - If it's real docker: container name 'taskmanager-db' is fine
# - If it's compose: service name 'taskmanager-db' is fine
docker exec -i "$DB_CID" \
  mariadb-dump -uroot -p"$DB_ROOT_PASSWORD" "$DB_NAME" > "$OUT_FILE"

if [[ ! -s "$OUT_FILE" ]]; then
  echo "❌ Backup failed — output file is empty: $OUT_FILE"
  exit 1
fi

echo "✅ Backup complete: $OUT_FILE"

