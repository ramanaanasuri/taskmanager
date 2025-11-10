#!/usr/bin/env bash
# scripts/db_rollback.sh
# Restore DB from a backup .sql file. By default, interactively pick from db/backups/.
# Usage:
#   scripts/db_rollback.sh                   # interactive pick
#   scripts/db_rollback.sh db/backups/file.sql  # restore from specific file
#
# It will:
#  - Create a safety backup BEFORE restore
#  - Restore the selected .sql over the current DB
set -a; source .env; set +a
set -euo pipefail

# --- Config (override via env if needed) ---
DB_NAME="${DB_NAME:-taskmanager}"
DB_ROOT_PASSWORD="${DB_ROOT_PASSWORD:-}"
BACKUP_DIR="${BACKUP_DIR:-db/backups}"

# Try to locate the DB container automatically
DB_CID="${DB_CID:-$(docker ps -qf name=taskmanager-db || true)}"
if [[ -z "$DB_CID" ]]; then
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

# --- Choose backup file ---
RESTORE_FILE="${1:-}"
if [[ -z "${RESTORE_FILE}" ]]; then
  echo "📂 Available backups in ${BACKUP_DIR}:"
  mapfile -t FILES < <(ls -1t ${BACKUP_DIR}/*.sql 2>/dev/null || true)
  if [[ ${#FILES[@]} -eq 0 ]]; then
    echo "❌ No .sql backups found in ${BACKUP_DIR}"
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
  echo "❎ Aborted."
  exit 0
fi

# --- Safety backup before restore ---
TS="$(date -u +%Y%m%dT%H%M%SZ)"
PRE_FILE="${BACKUP_DIR}/${DB_NAME}_pre_rollback_${TS}.sql"

echo "📦 Taking safety backup before restore: ${PRE_FILE}"
docker exec -i "${DB_CID}" mariadb-dump -uroot -p"${DB_ROOT_PASSWORD}" "${DB_NAME}" > "${PRE_FILE}"
if [[ ! -s "${PRE_FILE}" ]]; then
  echo "❌ Safety backup failed; file is empty: ${PRE_FILE}"
  exit 1
fi

# --- Restore ---
echo "♻️  Restoring from ${RESTORE_FILE} ..."
docker exec -i "${DB_CID}" mariadb -uroot -p"${DB_ROOT_PASSWORD}" "${DB_NAME}" < "${RESTORE_FILE}"

echo "✅ Restore complete."

# --- Quick sanity checks ---
echo "🔎 Sanity checks:"
docker exec -i "${DB_CID}" mariadb -uroot -p"${DB_ROOT_PASSWORD}" "${DB_NAME}" -e "SHOW TABLES;" | sed 's/^/  /'

