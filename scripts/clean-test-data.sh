#!/usr/bin/env bash
#
# clean-test-data.sh — remove data left behind by Karate/Playwright runs.
#
# Everything is scoped STRICTLY to the tester identity, so real user data is
# never touched. DRY-RUN by default (shows counts, deletes nothing); pass
# --apply to delete, which then asks for an explicit "YES" confirmation.
#
#   ./scripts/clean-test-data.sh                 # dry-run (safe) — just counts
#   ./scripts/clean-test-data.sh --apply         # delete (asks to confirm)
#   ./scripts/clean-test-data.sh --tester=x@y    # override the tester email
#
# The DB root password is read from the running MariaDB container's own env and
# is NEVER printed. No credentials appear in output — nothing to redact.
#
set -euo pipefail

TESTER="${TESTER_EMAIL:-api-tester@sriinfosoft.local}"
DB_CONTAINER="${DB_CONTAINER:-taskmanager-db}"
DB_NAME="${DB_NAME:-taskmanager}"
APPLY=0

for a in "$@"; do
  case "$a" in
    --apply)      APPLY=1 ;;
    --tester=*)   TESTER="${a#*=}" ;;
    -h|--help)    grep '^#' "$0" | sed 's/^# \{0,1\}//'; exit 0 ;;
    *) echo "unknown arg: $a (see --help)"; exit 2 ;;
  esac
done

# --- DB access via the container; password read from the container env (never printed) ---
PW="$(docker exec "$DB_CONTAINER" sh -lc 'printf "%s" "${MARIADB_ROOT_PASSWORD:-$MYSQL_ROOT_PASSWORD}"' 2>/dev/null || true)"
if [ -z "${PW}" ]; then
  echo "ERROR: could not read the DB root password from container '$DB_CONTAINER'."
  echo "       Is the container running?  docker ps | grep $DB_CONTAINER"
  exit 1
fi

sql() { docker exec -i -e MYSQL_PWD="$PW" "$DB_CONTAINER" mariadb -uroot -N -B "$DB_NAME" -e "$1"; }

has_table() {
  local n; n="$(sql "SELECT COUNT(*) FROM information_schema.tables WHERE table_schema='$DB_NAME' AND table_name='$1';" 2>/dev/null || echo 0)"
  [ "$n" = "1" ]
}

echo "======================================================"
echo " clean-test-data"
echo "   tester : $TESTER"
echo "   db     : $DB_NAME @ container $DB_CONTAINER"
echo "   mode   : $([ "$APPLY" -eq 1 ] && echo '*** APPLY (will delete) ***' || echo 'DRY-RUN (no changes)')"
echo "======================================================"
echo

# ------------------------------------------------------------------ counts
# Live Sessions
so=$(sql "SELECT COUNT(*) FROM session_offering WHERE mentor_email='$TESTER';")
sp=$(sql "SELECT COUNT(*) FROM session_participant WHERE session_offering_id IN (SELECT id FROM session_offering WHERE mentor_email='$TESTER');")
printf "Live Sessions      : %s offering(s), %s participant(s)\n" "$so" "$sp"

# InsightHub questions asked by the tester (+ their answers)
q=0; qa=0
if has_table insight_hub_questions; then
  q=$(sql "SELECT COUNT(*) FROM insight_hub_questions WHERE asked_by_email='$TESTER';")
  if has_table insight_hub_answers; then
    qa=$(sql "SELECT COUNT(*) FROM insight_hub_answers WHERE question_id IN (SELECT id FROM insight_hub_questions WHERE asked_by_email='$TESTER');")
  fi
fi
printf "InsightHub Q by tester : %s question(s), %s answer(s)\n" "$q" "$qa"

# InsightHub hubs created by the tester (+ memberships)
hubs=0; mem=0
if has_table insight_hubs; then
  hubs=$(sql "SELECT COUNT(*) FROM insight_hubs WHERE mentor_email='$TESTER';")
fi
if has_table insight_hub_members; then
  mem=$(sql "SELECT COUNT(*) FROM insight_hub_members WHERE member_email='$TESTER';")
fi
printf "InsightHub hubs/mem    : %s tester hub(s), %s tester membership(s)\n" "$hubs" "$mem"

# Casbin grants for the tester
cg=0
if has_table casbin_rule; then
  cg=$(sql "SELECT COUNT(*) FROM casbin_rule WHERE ptype='g' AND v0='$TESTER';")
fi
printf "Casbin grants          : %s grant(s)\n" "$cg"
echo

if [ "$APPLY" -eq 0 ]; then
  echo "DRY-RUN complete — nothing deleted."
  echo "Re-run with --apply to delete the above (scoped to $TESTER)."
  exit 0
fi

read -r -p "Delete ALL of the above (scoped to $TESTER)? type YES to proceed: " ok
[ "$ok" = "YES" ] || { echo "aborted — nothing deleted."; exit 1; }
echo

# ------------------------------------------------------------------ deletes (children first)
# Live Sessions
sql "DELETE FROM session_participant WHERE session_offering_id IN (SELECT id FROM session_offering WHERE mentor_email='$TESTER');"
sql "DELETE FROM session_offering WHERE mentor_email='$TESTER';"
echo "  deleted Live Session offerings + participants"

# InsightHub questions by tester (+ answers)
if has_table insight_hub_questions; then
  if has_table insight_hub_answers; then
    sql "DELETE FROM insight_hub_answers WHERE question_id IN (SELECT id FROM insight_hub_questions WHERE asked_by_email='$TESTER');"
  fi
  sql "DELETE FROM insight_hub_questions WHERE asked_by_email='$TESTER';"
  echo "  deleted InsightHub questions asked by tester (+ answers)"
fi

# InsightHub tester memberships + tester-created hubs (and any children in those hubs)
if has_table insight_hub_members; then
  sql "DELETE FROM insight_hub_members WHERE member_email='$TESTER';"
fi
if has_table insight_hubs; then
  # clear children of tester-created hubs before removing the hubs
  if has_table insight_hub_answers && has_table insight_hub_questions; then
    sql "DELETE FROM insight_hub_answers WHERE question_id IN (SELECT id FROM insight_hub_questions WHERE insight_hub_id IN (SELECT id FROM insight_hubs WHERE mentor_email='$TESTER'));"
  fi
  if has_table insight_hub_questions; then
    sql "DELETE FROM insight_hub_questions WHERE insight_hub_id IN (SELECT id FROM insight_hubs WHERE mentor_email='$TESTER');"
  fi
  if has_table insight_hub_members; then
    sql "DELETE FROM insight_hub_members WHERE insight_hub_id IN (SELECT id FROM insight_hubs WHERE mentor_email='$TESTER');"
  fi
  sql "DELETE FROM insight_hubs WHERE mentor_email='$TESTER';"
  echo "  deleted InsightHub tester memberships + tester-created hubs"
fi

# Casbin grants
if has_table casbin_rule; then
  sql "DELETE FROM casbin_rule WHERE ptype='g' AND v0='$TESTER';"
  echo "  deleted Casbin grants for tester"
fi

echo
echo "Done."
echo "NOTE: if Casbin grants were deleted, restart the backend so the enforcer"
echo "      reloads its policy:   docker restart taskmanager-backend"
