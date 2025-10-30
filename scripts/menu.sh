#!/usr/bin/env bash
set -euo pipefail

# ---- Config ---------------------------------------------------------------
CLOUD="${CLOUD:-gcp}"

BASE="docker-compose.yml"
OVERLAY_GCP="deployments/gcp/compose/docker-compose.gcp.yml"
OVERLAY_AWS="deployments/aws/compose/docker-compose.yml"

DB_CONT="taskmanager-db"
BE_CONT="taskmanager-backend"
FE_CONT="taskmanager-frontend"


# ---- Helpers --------------------------------------------------------------
compose() {
  case "$CLOUD" in
    gcp) docker compose -f "$BASE" -f "$OVERLAY_GCP" "$@" ;;
    aws) docker compose -f "$BASE" -f "$OVERLAY_AWS" "$@" ;;
    *)   echo "Unknown CLOUD='$CLOUD' (use gcp|aws)"; exit 1 ;;
  esac
}

load_env() {
  if [[ -f .env ]]; then
    set -a; source .env; set +a
  fi
}

print_header() {
  echo
  echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
  echo " TaskManager Orchestrator  (cloud: $CLOUD)"
  echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
}

status_block() {
  echo "➡️  Docker containers (docker ps):"
  docker ps --format "table {{.Names}}\t{{.Status}}\t{{.Ports}}"
  echo
  echo "➡️  Compose services (docker compose ps):"
  compose ps || true
  echo
}

# Find helper that prunes noisy dirs and forwards the rest of the args to find
find_pruned() {
  find . \
    \( -path '*/.git/*' -o -path '*/node_modules/*' -o -path '*/target/*' -o -path '*/dist/*' \) -prune -o \
    "$@"
}


# ---- Actions (stack) ------------------------------------------------------
action_status() {
  print_header
  status_block
  read -rp "Press Enter to continue…"
}

action_down() {
  print_header
  status_block
  echo "⏹  Stopping/removing stack…"
  compose down -v
  echo "✅ Done."
  read -rp "Press Enter to continue…"
}

action_build_up() {
  print_header
  status_block
  echo "🚀 Building & starting all services…"
  compose up -d --build
  echo "⏳ Waiting 5s then showing status…"
  sleep 5
  status_block
  read -rp "Press Enter to continue…"
}

action_logs_status() {
  print_header
  echo "🗒  Last 80 log lines for each container:"
  echo "---- $DB_CONT ----"; docker logs --tail=80 "$DB_CONT" || true; echo
  echo "---- $BE_CONT ----"; docker logs --tail=80 "$BE_CONT" || true; echo
  echo "---- $FE_CONT ----"; docker logs --tail=80 "$FE_CONT" || true; echo
  read -rp "Press Enter to continue…"
}

action_tail_backend() {
  print_header
  echo "📡 Tailing backend logs (Ctrl+C to stop)…"
  docker logs -f "$BE_CONT"
}

action_start_backend_only() {
  print_header
  echo "🧩 Starting backend only (build if needed)…"
  compose up -d --build "$BE_CONT"
  echo; status_block
  read -rp "Press Enter to continue…"
}

action_db_details() {
  print_header
  load_env
  : "${DB_NAME:=taskmanager}"
  : "${DB_ROOT_PASSWORD:=rootpassword}"

  echo "📚 DB info (container=$DB_CONT, db=$DB_NAME)"
  echo

  docker exec -i "$DB_CONT" mariadb \
    --protocol=TCP -h127.0.0.1 -P3306 \
    -uroot -p"$DB_ROOT_PASSWORD" -t \
    -e "
      SHOW DATABASES;
      USE \`$DB_NAME\`;
      SHOW TABLES;
      SELECT
        id, CAST(completed AS UNSIGNED) AS completed,
        created_at, title, updated_at, user_email
      FROM tasks
      ORDER BY id DESC
      LIMIT 20;
      SELECT COUNT(*) AS task_count FROM tasks;
    " || echo "⚠️  Could not query DB. Is $DB_CONT healthy and .env root password correct?"

  read -rp "Press Enter to continue…"
}

action_search_dbpass() {
  print_header
  echo "🔎 Search for DB_PASS or a specific password value in files"
  echo "1) Find occurrences of the token 'DB_PASS'"
  echo "2) Find a literal value (exact match)"
  read -rp "Choose 1 or 2: " mode
  case "$mode" in
    1)
      echo; echo "→ Searching for 'DB_PASS' (case-insensitive)…"
      # Use -print0 + xargs -0 so filenames with spaces work
      find_pruned -type f -print0 | xargs -0 grep -inH -- "db_pass" || true
      ;;
    2)
      read -rp "Enter the exact value to search for: " val
      [[ -z "$val" ]] && { echo "No value provided."; read -rp "Enter to continue…"; return; }
      echo; echo "→ Searching for literal value: $val"
      find_pruned -type f -print0 | xargs -0 grep -nH -- "$val" || true
      ;;
    *)
      echo "Invalid choice."
      ;;
  esac
  echo; read -rp "Press Enter to continue…"
}


# ---- NEW: File search utilities ------------------------------------------
action_find_exact_file() {
  print_header
  read -rp "Enter exact file name to find (e.g., README.md): " fname
  [[ -z "${fname:-}" ]] && { echo "No name provided."; read -rp "Enter to continue…"; return; }
  echo; echo "→ Searching for exact name: $fname"
  # -name matches exact; -iname for case-insensitive exact
  find_pruned -type f -name "$fname" -print || true
  echo; read -rp "Press Enter to continue…"
}

action_find_name_contains() {
  print_header
  read -rp "Enter substring to match in file names (e.g., .bak): " sub
  [[ -z "${sub:-}" ]] && { echo "No substring provided."; read -rp "Enter to continue…"; return; }
  echo; echo "→ Searching for file names containing: $sub"
  # -iname "*sub*" case-insensitive
  # We need to build a quoted pattern safely:
  pat="*${sub}*"
  find_pruned -type f -iname "*${sub}*" -print || true
  echo; read -rp "Press Enter to continue…"
}

action_find_grep_ci() {
  print_header
  read -rp "Enter case-insensitive content pattern to search: " pat
  [[ -z "${pat:-}" ]] && { echo "No pattern provided."; read -rp "Enter to continue…"; return; }
  echo; echo "→ Grep (CI) for: $pat"
  # Use grep -i -n -H on all files, excluding pruned dirs
  find_pruned -type f -print0 | xargs -0 grep -inH -- "$pat" || true
  echo; read -rp "Press Enter to continue…"
}


# ---- Menu loop ------------------------------------------------------------
while true; do
  clear
  print_header
  echo "Always-on precheck:"
  status_block
  cat <<MENU
Choose an option:
  1) Check container status (again)
  2) Bring DOWN the stack (all services)
  3) BUILD & start ALL services
  4) Show recent logs for DB/Backend/Frontend
  5) Tail backend logs (Ctrl+C to stop)
  6) Start backend ONLY
  7) DB details (show databases/tables + latest tasks)
  8) Search files for DB_PASS (or a value)
  9) Find a file by exact name
  10) Find files whose name contains a substring (e.g., ".bak")
  11) Find files whose contents contain a string (case-insensitive)
  12) Switch cloud profile (current: $CLOUD)
  q) Quit
MENU
  read -rp "Select: " ans
  case "$ans" in
    1)  action_status ;;
    2)  action_down ;;
    3)  action_build_up ;;
    4)  action_logs_status ;;
    5)  action_tail_backend ;;
    6)  action_start_backend_only ;;
    7)  action_db_details ;;
    8)  action_search_dbpass ;;
    9)  action_find_exact_file ;;
    10) action_find_name_contains ;;
    11) action_find_grep_ci ;;
    12)
        read -rp "Enter cloud profile (gcp|aws): " newc
        [[ "$newc" == "gcp" || "$newc" == "aws" ]] && CLOUD="$newc" || echo "Invalid; staying on $CLOUD"
        ;;
    q|Q) echo "Bye!"; exit 0 ;;
    *) echo "Invalid choice"; sleep 1 ;;
  esac
done

