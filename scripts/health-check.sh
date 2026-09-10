#!/usr/bin/env bash
# health-check.sh — validates the GCP Task Manager chain with timings.
# Usage: ./scripts/health-check.sh
# Reads API_TESTER_PASSWORD from .env in the repo root; never prints it.

set -u
ENV_FILE="$(dirname "$0")/../.env"
[ -f "$ENV_FILE" ] || ENV_FILE=".env"

FE="https://taskmanager.gcp.sriinfosoft.com"
API="https://api-taskmanager.gcp.sriinfosoft.com"

line() { printf -- "----------------------------------------\n"; }
check() { # $1 label, $2 url, $3 expected-status
  local out; out=$(curl -sk -o /dev/null -w "%{http_code} %{time_total}s" --max-time 60 "$2")
  local code=${out%% *}
  if [ "$code" = "$3" ]; then printf "✅ %-28s %s (want %s)\n" "$1" "$out" "$3"
  else printf "❌ %-28s %s (want %s)\n" "$1" "$out" "$3"; fi
}

line
echo "1) Endpoint reachability + latency (times >2s = trouble)"
line
check "Frontend"            "$FE/"                              200
check "Backend health"      "$API/actuator/health"              200
check "OAuth redirect (302)" "$API/oauth2/authorization/google" 302
check "Tester status"       "$API/api/tester/status"            200

line
echo "2) Tester auth (password from .env, not shown)"
line
PW=$(grep -E '^API_TESTER_PASSWORD=' "$ENV_FILE" 2>/dev/null | cut -d= -f2-)
if [ -z "${PW}" ]; then
  echo "⚠️  API_TESTER_PASSWORD not found in $ENV_FILE — skipping auth test"
else
  TOK=$(curl -sk --max-time 60 -X POST "$API/api/tester/auth" \
        -H "Content-Type: application/json" \
        -d "{\"username\":\"admin\",\"password\":\"$PW\"}" | jq -r '.token // empty')
  if [ -n "$TOK" ]; then echo "✅ Tester auth OK (token ${TOK:0:12}…)"
  else echo "❌ Tester auth FAILED"; fi
fi

line
echo "3) VM resources (login slowness usually shows up here)"
line
free -h
uptime
docker stats --no-stream --format "table {{.Name}}\t{{.CPUPerc}}\t{{.MemUsage}}"

line
echo "4) Backend errors in the last 15 min"
line
docker logs taskmanager-backend --since 15m 2>&1 \
  | grep -iE "ERROR|exception" | grep -v "Invalid JWT" | tail -5 \
  || echo "(none beyond stale-JWT noise)"
