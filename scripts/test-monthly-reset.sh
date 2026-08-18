#!/usr/bin/env bash
# ============================================================================
# test-monthly-reset.sh — end-to-end test of the monthly usage-counter reset
#
# What it does (fully automated, ~90s):
#   1. Seeds nonzero AI/SMS usage on the test account (direct SQL)
#   2. Recreates the backend with a fire-every-minute reset cron
#      (shell-env override - your .env is NEVER touched)
#   3. Polls the DB until counters hit zero (or 120s timeout)
#   4. Restores the backend with the normal monthly cron
#   5. Prints PASS/FAIL
#
# Usage:  ./scripts/test-monthly-reset.sh [email]     (default: ranasuri@gmail.com)
# Cloud is auto-detected like build.sh; runs on either box.
# ============================================================================
set -u
cd "$(dirname "$0")/.."

EMAIL="${1:-ranasuri@gmail.com}"
DEFAULT_CLOUD="gcp"
grep -qi "amazon linux" /etc/os-release 2>/dev/null && DEFAULT_CLOUD="aws"
OVERLAY="deployments/$DEFAULT_CLOUD/compose/docker-compose.$DEFAULT_CLOUD.yml"
if docker compose version >/dev/null 2>&1; then DC=(docker compose); else DC=(docker-compose); fi
COMPOSE() { "${DC[@]}" -p taskmanager -f docker-compose.yml -f "$OVERLAY" "$@"; }

SQL() { docker exec taskmanager-db sh -c "mariadb -u\"\$MYSQL_USER\" -p\"\$MYSQL_PASSWORD\" \"\$MYSQL_DATABASE\" -N -e \"$1\""; }

echo "== monthly-reset test  [cloud: $DEFAULT_CLOUD, user: $EMAIL] =="

echo "-- step 1: seed nonzero counters"
SQL "UPDATE users SET ai_requests_used=7, sms_credits_used=3 WHERE email='$EMAIL';"
echo "   seeded: $(SQL "SELECT ai_requests_used, sms_credits_used FROM users WHERE email='$EMAIL';")"

echo "-- step 2: recreate backend with fire-every-minute cron (env override, .env untouched)"
AI_RESET_CRON="0 * * * * *" COMPOSE up -d taskmanager-backend >/dev/null 2>&1
echo "   waiting for the next minute boundary + startup..."

echo "-- step 3: poll for reset (timeout 120s)"
PASSED=0
for i in $(seq 1 24); do
  sleep 5
  VALS=$(SQL "SELECT ai_requests_used, sms_credits_used FROM users WHERE email='$EMAIL';")
  echo "   t+$((i*5))s: $VALS"
  if [[ "$VALS" == *"0"*$'\t'*"0"* || "$VALS" =~ ^0[[:space:]]+0$ ]]; then PASSED=1; break; fi
done

echo "-- step 4: restore normal monthly cron"
COMPOSE up -d taskmanager-backend >/dev/null 2>&1

echo "-- step 5: verdict"
docker logs taskmanager-backend 2>&1 | grep "🔄" | tail -2
if [[ $PASSED -eq 1 ]]; then
  echo "PASS  counters were zeroed by the scheduled reset"
  exit 0
else
  echo "FAIL  counters not zeroed within timeout — check: docker logs taskmanager-backend | grep 🔄"
  exit 1
fi
