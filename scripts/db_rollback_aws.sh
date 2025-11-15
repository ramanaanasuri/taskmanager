#!/usr/bin/env bash
# scripts/db_rollback_aws.sh
# Restore DB from a backup .sql file (e.g. GCP → AWS clone)

set -euo pipefail

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

DB_NAME="${DB_NAME:-taskmanager}"
DB_ROOT_PASSWORD="${DB_ROOT_PASSWORD:-}"
BACKUP_DIR="${BACKUP_DIR:-db/backups}"
DB_SERVICE="${DB_CID:-taskmanager-db}"

if [[ -z "$DB_ROOT_PASSWORD" ]]; then
  echo "❌ DB_ROOT_PASSWORD is not set."
  exit 1
fi

# Detect whether docker compose / docker-compose exists
COMPOSE_CMD=""
if command -v docker &>/dev/null && docker compose version &>/dev/null; then
  COMPOSE_CMD="docker compose"
elif command -v docker-compose &>/dev/null; then
  COMPOSE_CMD="docker-compose"
fi

# Verify DB container/service is running
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

RESTORE_FILE="${1:-}"
if [[ -z "$RESTORE_FILE" ]]; then
  echo "❌ Usage: $0 path/to/backup.sql"
  exit 1
fi

# Normalize to absolute path
if [[ "$RESTORE_FILE" != /* ]]; then
  RESTORE_FILE="$PROJECT_ROOT/$RESTORE_FILE"
fi

if [[ ! -r "$RESTORE_FILE" ]]; then
  echo "❌ Backup file not readable: $RESTORE_FILE"
  exit 1
fi

echo "⚠️  This will restore '$RESTORE_FILE' into database '$DB_NAME' on '$DB_SERVICE'."
read -rp "Type 'RESTORE' to proceed: " CONFIRM
if [[ "$CONFIRM" != "RESTORE" ]]; then
  echo "❎ Aborted."
  exit 0
fi

mkdir -p "$BACKUP_DIR"

TS="$(date -u +%Y%m%dT%H%M%SZ)"
PRE_FILE="${BACKUP_DIR}/${DB_NAME}_pre_rollback_${TS}.sql"

echo "📦 Taking safety backup before restore: ${PRE_FILE}"

if [[ -n "$COMPOSE_CMD" ]]; then
  $COMPOSE_CMD exec -T "$DB_SERVICE" \
    mariadb-dump -uroot -p"$DB_ROOT_PASSWORD" "$DB_NAME" > "$PRE_FILE"
else
  docker exec -i "$DB_SERVICE" \
    mariadb-dump -uroot -p"$DB_ROOT_PASSWORD" "$DB_NAME" > "$PRE_FILE"
fi

if [[ ! -s "$PRE_FILE" ]]; then
  echo "❌ Safety backup failed; file is empty: $PRE_FILE"
  exit 1
fi

echo "♻️  Restoring from $RESTORE_FILE ..."

if [[ -n "$COMPOSE_CMD" ]]; then
  # NOTE: -T is critical here to avoid "input device is not a TTY"
  $COMPOSE_CMD exec -T "$DB_SERVICE" \
    mariadb -uroot -p"$DB_ROOT_PASSWORD" "$DB_NAME" < "$RESTORE_FILE"
else
  docker exec -i "$DB_SERVICE" \
    mariadb -uroot -p"$DB_ROOT_PASSWORD" "$DB_NAME" < "$RESTORE_FILE"
fi

echo "✅ Restore complete."

echo "🔎 Sanity check (SHOW TABLES):"
if [[ -n "$COMPOSE_CMD" ]]; then
  $COMPOSE_CMD exec -T "$DB_SERVICE" \
    mariadb -uroot -p"$DB_ROOT_PASSWORD" "$DB_NAME" \
    -e "SHOW TABLES;" | sed 's/^/  /'
else
  docker exec -i "$DB_SERVICE" \
    mariadb -uroot -p"$DB_ROOT_PASSWORD" "$DB_NAME" \
    -e "SHOW TABLES;" | sed 's/^/  /'
fi

