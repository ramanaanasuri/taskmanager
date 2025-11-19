#!/usr/bin/env bash
# scripts/db_rollback.sh
# Restore DB from a backup .sql file. By default, interactively pick from db/backups/.
# AWS-compatible version - copies file into container to avoid TTY/stdin issues
#
# Usage:
#   scripts/db_rollback.sh                   # interactive pick
#   scripts/db_rollback.sh db/backups/file.sql  # restore from specific file
#
# It will:
#  - Create a safety backup BEFORE restore
#  - Restore the selected .sql over the current DB

set -euo pipefail

# ------------------------------
# Resolve repo root and .env
# ------------------------------
ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
ENV_FILE="$ROOT_DIR/.env"

if [[ -f "$ENV_FILE" ]]; then
  echo "🔧 Loading environment variables from .env ..."
  set -a
  # shellcheck disable=SC1090
  source "$ENV_FILE"
  set +a
fi

# --- Config (override via env if needed) ---
DB_NAME="${DB_NAME:-taskmanager}"
DB_ROOT_PASSWORD="${DB_ROOT_PASSWORD:-}"
BACKUP_DIR="${BACKUP_DIR:-db/backups}"

# Use container name directly
DB_CONTAINER="${DB_CONTAINER:-taskmanager-db}"

# --- Safety checks ---
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
  echo "     DB_CONTAINER=<n> $0"
  exit 1
fi

mkdir -p "$ROOT_DIR/$BACKUP_DIR"

# --- Choose backup file ---
RESTORE_FILE="${1:-}"
if [[ -z "${RESTORE_FILE}" ]]; then
  echo "📂 Available backups in $ROOT_DIR/${BACKUP_DIR}:"
  mapfile -t FILES < <(ls -1t "$ROOT_DIR/${BACKUP_DIR}"/*.sql 2>/dev/null || true)
  if [[ ${#FILES[@]} -eq 0 ]]; then
    echo "❌ No .sql backups found in $ROOT_DIR/${BACKUP_DIR}"
    exit 1
  fi

  i=1
  for f in "${FILES[@]}"; do
    echo "  [$i] ${f}"
    ((i++))
  done
  read -rp "Select a backup number to restore: " CHOICE
  if ! [[ "${CHOICE}" =~ ^[0-9]+$ ]] || (( CHOICE < 1 || CHOICE > ${#FILES[@]} )); then
    echo "❌ Invalid selection."
    exit 1
  fi
  RESTORE_FILE="${FILES[$((CHOICE-1))]}"
fi

if [[ ! -r "${RESTORE_FILE}" ]]; then
  echo "❌ Backup file not readable: ${RESTORE_FILE}"
  exit 1
fi

echo "⚠️  This will restore '${RESTORE_FILE}' into database '${DB_NAME}'."
read -rp "Type 'RESTORE' to proceed: " CONFIRM
if [[ "${CONFIRM}" != "RESTORE" ]]; then
  echo "⏸ Aborted."
  exit 0
fi

# --- Safety backup before restore ---
TS="$(date -u +%Y%m%dT%H%M%SZ)"
PRE_FILE="$ROOT_DIR/${BACKUP_DIR}/${DB_NAME}_pre_rollback_${TS}.sql"

echo "📦 Taking safety backup before restore: ${PRE_FILE}"
docker exec -i "${DB_CONTAINER}" \
  mariadb-dump -uroot -p"${DB_ROOT_PASSWORD}" "${DB_NAME}" > "${PRE_FILE}"

if [[ ! -s "${PRE_FILE}" ]]; then
  echo "❌ Safety backup failed; file is empty: ${PRE_FILE}"
  exit 1
fi

# --- Restore using docker cp (avoids stdin/TTY issues) ---
echo "♻️  Restoring from ${RESTORE_FILE} ..."

# Use unique temp filename
TMP_FILE="/tmp/sql_restore_$$.sql"

# Step 1: Copy SQL file into container
docker cp "${RESTORE_FILE}" "${DB_CONTAINER}:${TMP_FILE}"

# Step 2: Execute SQL inside container
docker exec "${DB_CONTAINER}" sh -c "mariadb -uroot -p${DB_ROOT_PASSWORD} ${DB_NAME} < ${TMP_FILE}"

# Step 3: Cleanup
docker exec "${DB_CONTAINER}" rm -f "${TMP_FILE}"

echo "✅ Restore complete."

# --- Quick sanity checks ---
echo "🔎 Sanity checks:"
docker exec "${DB_CONTAINER}" \
  mariadb -uroot -p"${DB_ROOT_PASSWORD}" "${DB_NAME}" -e "SHOW TABLES;" | sed 's/^/  /'
