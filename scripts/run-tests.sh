#!/bin/bash
# run-tests.sh — unit and/or Karate tests in Docker Maven, output captured to a log.
# Usage:  ./scripts/run-tests.sh unit
#         ./scripts/run-tests.sh karate     (direct-to-backend via host network, default)
#         ./scripts/run-tests.sh karate gcp (through the public Caddy URL instead)
#         ./scripts/run-tests.sh all
# Log:    apps/backend/target/test-run-<timestamp>.log  (tee'd — you also see it live)

set -e
cd "$(dirname "$0")/.."                       # repo root
MODE="${1:-unit}"
KENV="${2:-local}"                            # local = direct backend; gcp/aws = public URL
TS=$(date +%Y%m%d-%H%M%S)
LOG="apps/backend/target/test-run-$TS.log"
mkdir -p apps/backend/target

TP=$(grep '^API_TESTER_PASSWORD=' .env | cut -d= -f2)

MVN_ARGS="test"
NET=""
case "$MODE" in
  unit)   MVN_ARGS="test" ;;
  karate) MVN_ARGS="test -Dtest=KarateApiTest -Dsurefire.failIfNoSpecifiedTests=false -Dkarate.api=true -Dkarate.env=$KENV -Dtester.password=\$TP -DargLine=-Xmx256m" ;;
  all)    MVN_ARGS="test -Dkarate.api=true -Dkarate.env=$KENV -Dtester.password=\$TP" ;;
  *) echo "usage: $0 unit|karate|all [local|gcp|aws]"; exit 1 ;;
esac
# local env talks to the backend's host-published port — needs host networking.
[ "$KENV" = "local" ] && NET="--network host"

# Karate tests a LIVE backend. Gate on health before spending a run — a cold
# Spring Boot start after build.sh 1 can take minutes on a small VM, and a
# timed-out callSingle poisons every scenario (learned 2026-09-08).
if [ "$MODE" != "unit" ]; then
  case "$KENV" in
    gcp) HURL="https://api-taskmanager.gcp.sriinfosoft.com/actuator/health" ;;
    aws) HURL="https://api-taskmanager.sriinfosoft.com/actuator/health" ;;
    *)   HURL="http://localhost:8080/actuator/health" ;;
  esac
  echo "═══ waiting for backend health at $HURL (up to 5 min)"
  HEALTHY=0
  for i in $(seq 1 30); do
    if curl -sf -o /dev/null --max-time 8 "$HURL"; then HEALTHY=1; break; fi
    sleep 10
  done
  if [ "$HEALTHY" -eq 1 ]; then
    echo "═══ backend healthy — starting tests"
  else
    echo "═══ backend NOT healthy after 5 min — aborting so no run is wasted"
    exit 1
  fi
fi

echo "═══ run-tests: mode=$MODE env=$KENV → $LOG"
docker run --rm $NET \
  -v "$(pwd)/apps/backend":/app -w /app \
  -v m2cache:/root/.m2 \
  -e TP="$TP" \
  maven:3.9-eclipse-temurin-17 \
  sh -c "mvn $MVN_ARGS" 2>&1 | tee "$LOG"

echo "═══ log saved: $LOG"
echo "═══ reports: target/site/jacoco/index.html · target/karate-reports/karate-summary.html"
