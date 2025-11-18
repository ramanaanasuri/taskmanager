#!/usr/bin/env bash
# scripts/db_backup_aws.sh
# Create a timestamped SQL backup for the running MariaDB container on AWS EC2.
# Uses the container name (taskmanager-db) and docker exec, never docker compose.

set -euo pipefail

# ------------------------------
# Resolve repo root and .env
# ------------------------------
ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
ENV_FILE="$ROOT_DIR/.env"

if [[ -f "$ENV_FILE" ]]; then
  set -a
  # shellcheck disable=SC1090
  source "$ENV_FILE"
  set +a
fi

# ------------------------------
# Config
# ------------------------------
DB_NAME="${DB_NAME:-taskmanager}"
DB_ROOT_PASSWORD="${DB_ROOT_PASSWORD:-}"
BACKUP_DIR="${BACKUP_DIR:-db/backups}"

# We will use the container *name*, not ID, to avoid docker compose v2 issues
DB_CONTAINER="${DB_CONTAINER:-taskmanager-db}"

# ------------------------------
# Safety checks
# ------------------------------
if [[ -z "$DB_ROOT_PASSWORD" ]]; then
  echo "❌ DB_ROOT_PASSWORD is empty."
  echo "   Set it in .env or export it in your shell, then re-run."
  exit 1
fi

# Ensure the container is actually running
if ! docker ps --format '{{.Names}}' | grep -qx "$DB_CONTAINER"; then
  echo "❌ Could not find a running DB container named '$DB_CONTAINER'."
  echo "   Current containers:"
  docker ps --format 'table {{.ID}}\t{{.Names}}\t{{.Status}}'
  echo "   If your DB container has a different name, run:"
  echo "     DB_CONTAINER=<name> ./scripts/db_backup_aws.sh"
  exit 1
fi

mkdir -p "$ROOT_DIR/$BACKUP_DIR"

TS="$(date -u +%Y%m%dT%H%M%SZ)"
OUT_FILE="$ROOT_DIR/${BACKUP_DIR}/${DB_NAME}_backup_${TS}.sql"

echo "📦 Creating backup from container '$DB_CONTAINER' into:"
echo "   $OUT_FILE"

# ------------------------------
# Run dump via docker exec
# ------------------------------
docker exec -i "$DB_CONTAINER" \
  mariadb-dump -uroot -p"$DB_ROOT_PASSWORD" "$DB_NAME" > "$OUT_FILE"

# ------------------------------
# Basic validation
# ------------------------------
if [[ ! -s "$OUT_FILE" ]]; then
  echo "❌ Backup file is empty or missing: $OUT_FILE"
  exit 1
fi

echo "✅ Backup complete: $OUT_FILE"

