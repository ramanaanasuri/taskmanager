#!/bin/bash
# run-e2e.sh — browser-layer (Playwright) tests against a DEPLOYED environment.
# Usage:  ./scripts/run-e2e.sh            (gcp, default)
#         ./scripts/run-e2e.sh aws
# Runs in the official Playwright Docker image: no Node, no browsers on the host.
# Report: apps/frontend/e2e/report/index.html  ·  Log: apps/frontend/e2e/e2e-run-<ts>.log

set -e
cd "$(dirname "$0")/.."
ENV="${1:-gcp}"

case "$ENV" in
  gcp) BASE="https://taskmanager.gcp.sriinfosoft.com"; API="https://api-taskmanager.gcp.sriinfosoft.com" ;;
  aws) BASE="https://taskmanager.sriinfosoft.com";     API="https://api-taskmanager.sriinfosoft.com" ;;
  *) echo "usage: $0 [gcp|aws]"; exit 1 ;;
esac

TP=$(grep '^API_TESTER_PASSWORD=' .env | cut -d= -f2)
[ -n "$TP" ] || { echo "API_TESTER_PASSWORD not found in .env"; exit 1; }

# Same idea as the karate health gate: don't spend a run on a dead target.
echo "═══ waiting for backend health at $API/actuator/health (up to 3 min)"
HEALTHY=0
for i in $(seq 1 18); do
  if curl -sf -o /dev/null --max-time 8 "$API/actuator/health"; then HEALTHY=1; break; fi
  sleep 10
done
if [ "$HEALTHY" -ne 1 ]; then echo "═══ backend NOT healthy — aborting"; exit 1; fi

TS=$(date +%Y%m%d-%H%M%S)
LOG="apps/frontend/e2e/e2e-run-$TS.log"
echo "═══ e2e: env=$ENV base=$BASE → $LOG"

docker run --rm --network host --ipc=host \
  -v "$(pwd)/apps/frontend/e2e":/e2e -w /e2e \
  -e PW_BASE_URL="$BASE" -e PW_API_URL="$API" \
  -e PW_TESTER_USER="${TESTER_USER:-admin}" -e TP="$TP" \
  mcr.microsoft.com/playwright:v1.63.0-jammy \
  bash -c 'PW_TESTER_PASSWORD="$TP" npm i --silent && PW_TESTER_PASSWORD="$TP" npx playwright test' 2>&1 | tee "$LOG"

echo "═══ log saved: $LOG"
echo "═══ report: apps/frontend/e2e/report/index.html"
