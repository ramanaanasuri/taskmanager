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
  -d, --db <n>          Override DB name (default: ${DB_NAME})
  -c, --container <n>   DB container name (default: ${CONTAINER})
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
# Actions: core tables
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

# ------------------------------
# Actions: push_subscriptions
# ------------------------------
action_describe_push_subscriptions() {
  echo "Describing table: push_subscriptions"
  pretty -e "USE \`$DB_NAME\`; DESCRIBE \`push_subscriptions\`;"
}

action_list_push_subscriptions() {
  echo "Latest push_subscriptions:"
  pretty -e "USE \`$DB_NAME\`;
    SELECT
      id,
      user_email,
      device_type,
      device_name,
      browser,
      os,
      DATE_FORMAT(last_used_at, '%Y-%m-%d %H:%i:%s') AS last_used_at,
      LEFT(endpoint, 80) AS endpoint_preview,
      DATE_FORMAT(created_at, '%Y-%m-%d %H:%i:%s') AS created_at,
      DATE_FORMAT(updated_at, '%Y-%m-%d %H:%i:%s') AS updated_at
    FROM \`push_subscriptions\`
    ORDER BY id DESC
    LIMIT 50;"
}

# ------------------------------
# Actions: notification_logs
# ------------------------------
action_describe_notification_logs() {
  echo "Describing table: notification_logs"
  pretty -e "USE \`$DB_NAME\`; DESCRIBE \`notification_logs\`;"
}

action_list_notification_logs() {
  echo "Latest notification_logs:"
  pretty -e "USE \`$DB_NAME\`;
    SELECT
      id,
      task_id,
      user_email,
      notification_type,
      status,
      LEFT(error_message, 120) AS error_message_preview,
      device_type,
      LEFT(sent_to_endpoint, 80) AS endpoint_preview,
      DATE_FORMAT(created_at, '%Y-%m-%d %H:%i:%s') AS created_at
    FROM \`notification_logs\`
    ORDER BY id DESC
    LIMIT 50;"
}

# ------------------------------
# Actions: tasks – extra views
# ------------------------------
action_list_tasks_with_priority() {
  local TBL; TBL="$(detect_tasks_table)"
  echo "Tasks with Priority and Scheduled Date:"
  pretty -e "USE \`$DB_NAME\`;
    SELECT
      id,
      CASE WHEN completed=1 THEN '✔' ELSE '' END AS done,
      title,
      priority,
      CASE 
        WHEN due_date IS NULL THEN 'Not scheduled'
        ELSE DATE_FORMAT(due_date, '%Y-%m-%d %H:%i')
      END AS scheduled,
      user_email
    FROM \`$TBL\`
    ORDER BY 
      CASE priority 
        WHEN 'HIGH' THEN 1 
        WHEN 'MEDIUM' THEN 2 
        WHEN 'LOW' THEN 3 
      END,
      due_date ASC
    LIMIT 50;"
}

action_task_stats() {
  local TBL; TBL="$(detect_tasks_table)"
  echo "Task Statistics by Priority:"
  pretty -e "USE \`$DB_NAME\`;
    SELECT 
      priority,
      COUNT(*) AS total,
      SUM(CASE WHEN completed=1 THEN 1 ELSE 0 END) AS completed,
      SUM(CASE WHEN completed=0 THEN 1 ELSE 0 END) AS pending,
      SUM(CASE WHEN due_date IS NOT NULL THEN 1 ELSE 0 END) AS with_schedule,
      SUM(CASE WHEN due_date IS NULL THEN 1 ELSE 0 END) AS no_schedule
    FROM \`$TBL\`
    GROUP BY priority
    ORDER BY 
      CASE priority 
        WHEN 'HIGH' THEN 1 
        WHEN 'MEDIUM' THEN 2 
        WHEN 'LOW' THEN 3 
      END;"
}

action_overdue_tasks() {
  local TBL; TBL="$(detect_tasks_table)"
  echo "Overdue Tasks (not completed, past due date):"
  pretty -e "USE \`$DB_NAME\`;
    SELECT
      id,
      title,
      priority,
      DATE_FORMAT(due_date, '%Y-%m-%d %H:%i') AS was_due,
      TIMESTAMPDIFF(DAY, due_date, NOW()) AS days_overdue,
      user_email
    FROM \`$TBL\`
    WHERE completed = 0 
      AND due_date IS NOT NULL 
      AND due_date < NOW()
    ORDER BY due_date ASC;"
}

action_upcoming_tasks() {
  local TBL; TBL="$(detect_tasks_table)"
  echo "Upcoming Tasks (next 7 days):"
  pretty -e "USE \`$DB_NAME\`;
    SELECT
      id,
      title,
      priority,
      DATE_FORMAT(due_date, '%Y-%m-%d %H:%i') AS scheduled,
      TIMESTAMPDIFF(DAY, NOW(), due_date) AS days_until,
      user_email
    FROM \`$TBL\`
    WHERE completed = 0 
      AND due_date IS NOT NULL 
      AND due_date >= NOW()
      AND due_date <= DATE_ADD(NOW(), INTERVAL 7 DAY)
    ORDER BY due_date ASC;"
}

action_tasks_by_priority() {
  local priority TBL
  echo "Choose priority:"
  echo "1) HIGH"
  echo "2) MEDIUM"
  echo "3) LOW"
  read -rp "Enter choice: " choice
  
  case "$choice" in
    1) priority="HIGH";;
    2) priority="MEDIUM";;
    3) priority="LOW";;
    *) echo "Invalid choice"; return 1;;
  esac
  
  TBL="$(detect_tasks_table)"
  echo "Tasks with $priority priority:"
  pretty -e "USE \`$DB_NAME\`;
    SELECT
      id,
      CASE WHEN completed=1 THEN '✔' ELSE '' END AS done,
      title,
      CASE 
        WHEN due_date IS NULL THEN 'Not scheduled'
        ELSE DATE_FORMAT(due_date, '%Y-%m-%d %H:%i')
      END AS scheduled,
      user_email
    FROM \`$TBL\`
    WHERE priority = '$priority'
    ORDER BY due_date ASC;"
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
  echo "=== Interactive SQL Shell ==="
  echo "Opening MariaDB shell for database: $DB_NAME"
  echo ""
  echo "Commands you can use:"
  echo "  - Type any SQL commands and press Enter"
  echo "  - Use 'SHOW TABLES;' to see all tables"
  echo "  - Use 'SELECT * FROM tasks LIMIT 10;' to query tasks"
  echo "  - Type 'exit' or 'quit' to return to menu"
  echo "  - Press Ctrl+C to abort a query"
  echo ""
  read -rp "Press Enter to open SQL shell..."
  
  docker exec -it "$CONTAINER" mariadb \
    --protocol=TCP -h127.0.0.1 -P3306 \
    -uroot -p"$DB_ROOT_PASSWORD" "$DB_NAME"
  
  echo ""
  echo "✅ SQL shell closed."
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

=== Basic Operations ===
1) Show databases
2) Show tables in $DB_NAME
3) Describe tasks table
4) List latest 50 tasks (basic)

=== Push Subscription / Notification Logs ===
10) Describe push_subscriptions table
11) List latest 50 push_subscriptions
12) Describe notification_logs table
13) List latest 50 notification_logs

=== Task Mutations ===
5) Insert test task
6) Mark task complete by id
7) Delete task by id
8) Seed DB & app user  (create DB, user, grant)

=== Priority & Schedule Views ===
p) List tasks with Priority & Scheduled Date
s) Task statistics by priority
o) Show overdue tasks
u) Show upcoming tasks (next 7 days)
f) Filter tasks by priority

=== Other ===
9) Count tasks by user email
r) Interactive SQL Shell (MariaDB prompt)
q) Quit
EOF
  read -rp "Choose an option: " choice
  case "$choice" in
    1) action_show_databases; pause;;
    2) action_show_tables; pause;;
    3) action_describe_tasks; pause;;
    4) action_list_tasks; pause;;

    10) action_describe_push_subscriptions; pause;;
    11) action_list_push_subscriptions; pause;;
    12) action_describe_notification_logs; pause;;
    13) action_list_notification_logs; pause;;

    5) action_insert_task; pause;;
    6) action_update_complete; pause;;
    7) action_delete_task; pause;;
    8) action_seed_app_user; pause;;
    9) action_count_by_email; pause;;
    p|P) action_list_tasks_with_priority; pause;;
    s|S) action_task_stats; pause;;
    o|O) action_overdue_tasks; pause;;
    u|U) action_upcoming_tasks; pause;;
    f|F) action_tasks_by_priority; pause;;
    r|R) action_raw_sql; pause;;
    q|Q) exit 0;;
    *) echo "Invalid option"; sleep 1;;
  esac
done

