#!/usr/bin/env bash
# scripts/db_backup.sh
# Create a timestamped SQL backup for the running MariaDB/MySQL container.
# Saves to repo-relative db/backups/
set -a; source .env; set +a
set -euo pipefail

# --- Config (override via env if needed) ---
DB_NAME="${DB_NAME:-taskmanager}"
DB_ROOT_PASSWORD="${DB_ROOT_PASSWORD:-}"
BACKUP_DIR="${BACKUP_DIR:-db/backups}"

# Try to locate the DB container automatically
DB_CID="${DB_CID:-$(docker ps -qf name=taskmanager-db || true)}"
if [[ -z "$DB_CID" ]]; then
  # fallback: common names
  DB_CID="$(docker ps -qf name=mariadb || true)"
fi

# --- Safety checks ---
if [[ -z "$DB_CID" ]]; then
  echo "❌ Could not find a running DB container (e.g., taskmanager-db)."
  echo "   Set DB_CID=<container_id_or_name> and re-run."
  exit 1
fi

if [[ -z "${DB_ROOT_PASSWORD}" ]]; then
  echo "❌ DB_ROOT_PASSWORD is empty."
  echo "   Export DB_ROOT_PASSWORD=<value> then re-run."
  exit 1
fi

mkdir -p "${BACKUP_DIR}"

TS="$(date -u +%Y%m%dT%H%M%SZ)"
OUT_FILE="${BACKUP_DIR}/${DB_NAME}_backup_${TS}.sql"

echo "📦 Creating backup: ${OUT_FILE}"
# Dump from inside the container; write to host file
docker exec -i "${DB_CID}" mariadb-dump -uroot -p"${DB_ROOT_PASSWORD}" "${DB_NAME}" > "${OUT_FILE}"

# Basic validation
if [[ ! -s "${OUT_FILE}" ]]; then
  echo "❌ Backup file is empty or missing: ${OUT_FILE}"
  exit 1
fi

echo "✅ Backup complete: ${OUT_FILE}"

