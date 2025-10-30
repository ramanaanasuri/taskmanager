#!/usr/bin/env bash
# TaskManager DB helper (MariaDB inside Docker)
# - Reads DB creds from .env if present
# - Lets you set root password interactively or by flag
# - Offers a menu of common queries for your `tasks` table

set -euo pipefail

# ------------------------------
# Defaults & .env loading
# ------------------------------
ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
ENV_FILE="$ROOT_DIR/.env"
CONTAINER="${CONTAINER:-taskmanager-db}"

# Load .env if present (export all vars while reading)
if [[ -f "$ENV_FILE" ]]; then
  set -a
  # shellcheck disable=SC1090
  source "$ENV_FILE"
  set +a
fi

DB_NAME="${DB_NAME:-taskmanager}"
DB_USER="${DB_USER:-taskuser}"
DB_PASS="${DB_PASSWORD:-taskpassword}"

# ------------------------------
# CLI flags
# ------------------------------
SHOW_HELP=0
OVERRIDE_DB_NAME=""
OVERRIDE_CONTAINER=""
OVERRIDE_ROOT_PW=""

while [[ $# -gt 0 ]]; do
  case "$1" in
    -h|--help) SHOW_HELP=1; shift;;
    -d|--db)   OVERRIDE_DB_NAME="${2:-}"; shift 2;;
    -c|--container) OVERRIDE_CONTAINER="${2:-}"; shift 2;;
    -p|--root-pw)   OVERRIDE_ROOT_PW="${2:-}"; shift 2;;
    *) echo "Unknown arg: $1"; SHOW_HELP=1; shift;;
  esac
done

if [[ $SHOW_HELP -eq 1 ]]; then
  cat <<EOF
Usage: CONTAINER=name ./scripts/db_tool.sh [options]

Options:
  -d, --db <name>          Override DB name (default: ${DB_NAME})
  -c, --container <name>   DB container name (default: ${CONTAINER})
  -p, --root-pw <value>    MariaDB root password (otherwise prompted)

Environment (from .env if present):
  DB_NAME, DB_USER, DB_PASS, DB_ROOT_PASSWORD
EOF
  exit 0
fi

[[ -n "$OVERRIDE_DB_NAME" ]] && DB_NAME="$OVERRIDE_DB_NAME"
[[ -n "$OVERRIDE_CONTAINER" ]] && CONTAINER="$OVERRIDE_CONTAINER"
if [[ -n "$OVERRIDE_ROOT_PW" ]]; then
  DB_ROOT_PASSWORD="$OVERRIDE_ROOT_PW"
fi

# ------------------------------
# Require/Prompt for root password
# ------------------------------
if [[ -z "${DB_ROOT_PASSWORD:-}" ]]; then
  read -rs -p "Enter MariaDB root password: " DB_ROOT_PASSWORD; echo
fi

# ------------------------------
# Helpers
# ------------------------------

# escape single quotes for SQL literals
sql_escape() { printf "%s" "$1" | sed "s/'/''/g"; }

db() { # non-TTY, suitable for piping
  docker exec -i "$CONTAINER" mariadb \
    --protocol=TCP -h127.0.0.1 -P3306 \
    -uroot -p"$DB_ROOT_PASSWORD" "$@"
}

pretty() { # TTY with borders
  docker exec -it "$CONTAINER" mariadb \
    --protocol=TCP -h127.0.0.1 -P3306 \
    -uroot -p"$DB_ROOT_PASSWORD" -t "$@"
}

detect_tasks_table() {
  # returns a table name that looks like tasks (tasks/task)
  local t
  t="$(db -N -e "SHOW TABLES FROM \`$DB_NAME\` LIKE '%task%';" | head -n1 || true)"
  if [[ -z "$t" ]]; then
    echo "tasks"   # fallback
  else
    echo "$t"
  fi
}

pause() { read -rp "Press Enter to continue…"; }

# ------------------------------
# Actions
# ------------------------------
action_show_databases() {
  pretty -e "SHOW DATABASES;"
}

action_show_tables() {
  pretty -e "USE \`$DB_NAME\`; SHOW TABLES;"
}

action_describe_tasks() {
  local TBL; TBL="$(detect_tasks_table)"
  echo "Describing table: $TBL"
  pretty -e "USE \`$DB_NAME\`; DESCRIBE \`$TBL\`;"
}

action_list_tasks() {
  local TBL; TBL="$(detect_tasks_table)"
  pretty -e "USE \`$DB_NAME\`;
    SELECT
      id,
      CASE WHEN completed=1 THEN '✔' ELSE '' END AS done,
      DATE_FORMAT(created_at, '%Y-%m-%d %H:%i:%s') AS created_at,
      title,
      DATE_FORMAT(updated_at, '%Y-%m-%d %H:%i:%s') AS updated_at,
      user_email
    FROM \`$TBL\`
    ORDER BY id DESC
    LIMIT 50;"
}

action_insert_task() {
  local title email TBL
  read -rp "Title: " title
  read -rp "User email (required): " email

  if [[ -z "$title" || -z "$email" ]]; then
    echo "Title and user email are required."
    return 1
  fi

  TBL="$(detect_tasks_table)"

  # Insert using only schema columns: id (auto), completed, created_at, title, updated_at, user_email
  db -t -e "
    USE \`${DB_NAME}\`;
    INSERT INTO \`${TBL}\`
      (completed, created_at, title, updated_at, user_email)
    VALUES
      (0, NOW(6), '$(sql_escape "$title")', NOW(6), '$(sql_escape "$email")');
    SELECT LAST_INSERT_ID() AS new_id;"
}

action_update_complete() {
  local id
  read -rp "Task id to mark complete: " id
  local TBL; TBL="$(detect_tasks_table)"
  pretty -e "USE \`$DB_NAME\`;
    UPDATE \`$TBL\` SET completed=1 WHERE id=$id;
    SELECT * FROM \`$TBL\` WHERE id=$id;"
}

action_delete_task() {
  local id
  read -rp "Task id to delete: " id
  local TBL; TBL="$(detect_tasks_table)"
  pretty -e "USE \`$DB_NAME\`;
    DELETE FROM \`$TBL\` WHERE id=$id;
    SELECT ROW_COUNT() AS deleted_rows;"
}

action_count_by_email() {
  local email
  read -rp "Email to count tasks for: " email
  local TBL; TBL="$(detect_tasks_table)"
  pretty -e "USE \`$DB_NAME\`;
    SELECT '$email' AS user_email, COUNT(*) AS task_count
    FROM \`$TBL\` WHERE user_email='$email';"
}

action_seed_app_user() {
  echo "Seeding DB ($DB_NAME) and app user ($DB_USER)…"
  db -e "CREATE DATABASE IF NOT EXISTS \`$DB_NAME\`;"
  db -e "CREATE USER IF NOT EXISTS '$DB_USER'@'%' IDENTIFIED BY '$DB_PASS';"
  db -e "GRANT ALL PRIVILEGES ON \`$DB_NAME\`.* TO '$DB_USER'@'%';"
  db -e "FLUSH PRIVILEGES;"
  echo "✅ Done."
}

action_raw_sql() {
  echo "Enter SQL to run against \`$DB_NAME\`. End with EOF (Ctrl+D):"
  db -e "USE \`$DB_NAME\`;"
  db -t <<'SQL'
-- You can paste multi-line SQL here; end with Ctrl+D
SQL
  echo "Tip: For multi-line against your DB, do:"
  echo "db <<SQL"
  echo "USE \`$DB_NAME\`;"
  echo "-- your statements"
  echo "SQL"
}

action_mark_complete() {
  local id TBL
  read -rp "Task id to mark complete: " id
  TBL="$(detect_tasks_table)"
  db -t -e "
    USE \`${DB_NAME}\`;
    UPDATE \`${TBL}\`
      SET completed = 1, updated_at = NOW(6)
    WHERE id = ${id};
    SELECT ROW_COUNT() AS rows_changed;"
}


# ------------------------------
# Menu
# ------------------------------
while true; do
  clear
  cat <<EOF
TaskManager DB Tool  (container: $CONTAINER, db: $DB_NAME)

1) Show databases
2) Show tables in $DB_NAME
3) Describe tasks table
4) List latest 50 tasks (pretty)
5) Insert test task
6) Mark task complete by id
7) Delete task by id
8) Seed DB & app user  (create DB, user, grant)
9) Count tasks by user email
r) Raw SQL prompt
q) Quit
EOF
  read -rp "Choose an option: " choice
  case "$choice" in
    1) action_show_databases; pause;;
    2) action_show_tables; pause;;
    3) action_describe_tasks; pause;;
    4) action_list_tasks; pause;;
    5) action_insert_task; pause;;
    6) action_update_complete; pause;;
    7) action_delete_task; pause;;
    8) action_seed_app_user; pause;;
    9) action_count_by_email; pause;;
    r|R) action_raw_sql; pause;;
    q|Q) exit 0;;
    *) echo "Invalid option"; sleep 1;;
  esac
done

