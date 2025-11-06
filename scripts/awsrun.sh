#!/usr/bin/env bash
set -euo pipefail

# ---- Config ---------------------------------------------------------------
CLOUD="${CLOUD:-aws}"

BASE="docker-compose.yml"
OVERLAY_GCP="deployments/gcp/compose/docker-compose.gcp.yml"
OVERLAY_AWS="deployments/aws/compose/docker-compose.aws.yml"

DB_CONT="taskmanager-db"
BE_CONT="taskmanager-backend"
FE_CONT="taskmanager-frontend"


# ---- Helpers --------------------------------------------------------------
compose() {
  case "$CLOUD" in
    gcp) docker-compose -f "$BASE" -f "$OVERLAY_GCP" "$@" ;;
    aws) docker-compose -f "$BASE" -f "$OVERLAY_AWS" "$@" ;;
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
  compose down --remove-orphans || true
  echo "✅ Done."
  read -rp "Press Enter to continue…"
}

action_stop() {
  print_header
  status_block
  echo "⏹  Stopping stack…"
  compose stop || true
  echo "✅ Done."
  read -rp "Press Enter to continue…"
}

action_start() {
  print_header
  status_block
  echo "⏹  Starting stack…"
  compose start || true
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

action_find_literal_lines() {
  print_header
  read -rp "Enter the exact string to search for (spaces OK): " needle
  [[ -z "${needle:-}" ]] && { echo "No search string provided."; read -rp "Press Enter to continue…"; return; }

  echo
  read -rp "Case-insensitive? [y/N]: " ci
  if [[ "${ci,,}" == "y" ]]; then
    CI_FLAG="-i"
  else
    CI_FLAG=""
  fi

  echo
  echo "→ Searching for literal string:"
  printf '   "%s"\n\n' "$needle"

  # Print filename:line:content for matches; treat pattern as fixed string (-F)
  # Use -n to always print line numbers; -H to force filenames.
  # Use -Z pipeline with xargs -0 to handle weird filenames safely.
  find_pruned -type f -print0 \
    | xargs -0 grep -nH ${CI_FLAG} -F -- "$needle" || true

  echo
  read -rp "Press Enter to continue…"
}

action_replace_literal() {
  print_header
  read -rp "Enter the EXACT string to replace (spaces OK): " from
  [[ -z "${from:-}" ]] && { echo "No source string provided."; read -rp "Press Enter to continue…"; return; }

  read -rp "Enter the replacement string: " to
  echo
  read -rp "Case-insensitive replace? [y/N]: " ci
  if [[ "${ci,,}" == "y" ]]; then
    CI_FLAG="I"     # GNU sed: I = case-insensitive for s///
    GREP_CI="-i"
  else
    CI_FLAG=""
    GREP_CI=""
  fi

  echo
  echo "→ Preview: files that contain the source string:"
  find_pruned -type f -print0 | xargs -0 grep -lH ${GREP_CI} -F -- "$from" || true

  echo
  read -rp "Would you like to perform a DRY RUN first? [y/N]: " dry
  if [[ "${dry,,}" == "y" ]]; then
    echo
    echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
    echo "   🔍 DRY RUN — showing differences (no changes)"
    echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
    echo
    find_pruned -type f -print0 | while IFS= read -r -d '' f; do
      if grep -q ${GREP_CI} -F -- "$from" "$f"; then
        echo "--- $f ---"
        # Show surrounding lines with differences (like git diff)
        sed -n "/${from}/I{
          s|${from}|${to}|I
          p
        }" "$f" | awk '{print "    " $0}'
        echo
      fi
    done
    echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
    echo "   End of DRY RUN preview"
    echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
    echo
    read -rp "Press Enter to continue…"
    return
  fi

  echo
  read -rp "Proceed with in-place replacement and create backups? [y/N]: " ok
  [[ "${ok,,}" != "y" ]] && { echo "Cancelled."; read -rp "Press Enter to continue…"; return; }

  ts="$(date +%F-%H%M%S)"

  # Escape slashes, pipes, ampersands, and backslashes so sed doesn't break
  esc() { printf '%s' "$1" | sed -e 's/[\/&|\\]/\\&/g'; }
  from_esc="$(esc "$from")"
  to_esc="$(esc "$to")"

  changed=0
  while IFS= read -r -d '' f; do
    # Skip binaries
    if command -v file >/dev/null 2>&1 && file -b --mime "$f" | grep -qi binary; then
      continue
    fi
    # Process only files containing the pattern
    if grep -q ${GREP_CI} -F -- "$from" "$f"; then
      cp -p -- "$f" "$f.bak.$ts"
      sed "s|${from_esc}|${to_esc}|g${CI_FLAG}" "$f" > "$f.__tmp__.$ts" && mv "$f.__tmp__.$ts" "$f"
      printf 'Rewrote: %s (backup: %s)\n' "$f" "$f.bak.$ts"
      changed=$((changed+1))
    fi
  done < <(find_pruned -type f -print0)

  echo
  echo "Done. Files changed: $changed"
  echo "Backups created with suffix: .bak.$ts"
  echo
  read -rp "Press Enter to continue…"
}

action_delete_by_extension() {
  print_header
  echo "Delete files mode:"
  echo "  1) Filename CONTAINS substring (e.g., .bak anywhere)"
  echo "  2) Filename ENDS WITH extension (e.g., *.bak)"
  read -rp "Choose 1 or 2: " mode

  if [[ "$mode" == "1" ]]; then
    read -rp "Enter substring to match anywhere (e.g., .bak): " needle
    [[ -z "${needle:-}" ]] && { echo "No substring entered."; read -rp "Press Enter to continue…"; return; }
    # case-insensitive “contains” match
    FIND_EXPR=(-type f -iname "*${needle}*")
  elif [[ "$mode" == "2" ]]; then
    read -rp "Enter extension (e.g., .bak): " ext
    [[ -z "${ext:-}" ]] && { echo "No extension entered."; read -rp "Press Enter to continue…"; return; }
    # case-insensitive “ends-with” match
    FIND_EXPR=(-type f -iname "*${ext}")
  else
    echo "Invalid choice."
    read -rp "Press Enter to continue…"
    return
  fi

  echo
  echo "→ Searching for matches…"
  echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"

  mapfile -d '' matches < <(
    find . \
      \( -path '*/.git/*' -o -path '*/node_modules/*' -o -path '*/target/*' -o -path '*/dist/*' \) -prune -o \
      "${FIND_EXPR[@]}" -print0
  )

  if (( ${#matches[@]} == 0 )); then
    echo "No matching files found."
    echo
    read -rp "Press Enter to continue…"
    return
  fi

  # Preview list
  for f in "${matches[@]}"; do
    printf '%s\n' "$f"
  done

  echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
  echo
  read -rp "Are you sure you want to delete ALL of the above? [y/N]: " confirm
  [[ "${confirm,,}" != "y" ]] && { echo "Cancelled."; read -rp "Press Enter to continue…"; return; }

  echo
  echo "Deleting files…"
  for f in "${matches[@]}"; do
    rm -f -- "$f" && echo "Deleted: $f"
  done

  echo
  echo "✅ Cleanup complete."
  echo
  read -rp "Press Enter to continue…"
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
  2) Start the stack (all services)
  3) Stop the stack (all services)
  4) Bring DOWN the stack (all services)
  5) BUILD & start ALL services
  6) Show recent logs for DB/Backend/Frontend
  7) Tail backend logs (Ctrl+C to stop)
  8) Start backend ONLY
  9) DB details (show databases/tables + latest tasks)
  10) Search files for DB_PASS (or a value)
  11) Find a file by exact name
  12) Find files whose name contains a substring (e.g., ".bak")
  13) Find files whose contents contain a string (case-insensitive)
  14) Switch cloud profile (current: $CLOUD)
  15) Search literal string (print matching lines)
  16) Replace literal string across files (with backups)
  17) Delete files whose NAME contains a substring (or ends with an extension)
  q) Quit
MENU
  read -rp "Select: " ans
  case "$ans" in
    1)  action_status ;;
    2)  action_start ;;
    3)  action_stop ;;
    4)  action_down ;;
    5)  action_build_up ;;
    6)  action_logs_status ;;
    7)  action_tail_backend ;;
    8)  action_start_backend_only ;;
    9)  action_db_details ;;
    10) action_search_dbpass ;;
    11) action_find_exact_file ;;
    12) action_find_name_contains ;;
    13) action_find_grep_ci ;;
    14)
        read -rp "Enter cloud profile (gcp|aws): " newc
        [[ "$newc" == "gcp" || "$newc" == "aws" ]] && CLOUD="$newc" || echo "Invalid; staying on $CLOUD"
        ;;
    15) action_find_literal_lines ;;
    16) action_replace_literal ;;
    17) action_delete_by_extension ;;
    q|Q) echo "Bye!"; exit 0 ;;
    *) echo "Invalid choice"; sleep 1 ;;
  esac
done

