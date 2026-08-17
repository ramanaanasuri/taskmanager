#!/usr/bin/env bash
# ============================================================================
# build.sh — one script for every build/deploy operation, by number or name
#
# Usage:
#   ./scripts/build.sh <option> [cloud] [service]
#   ./scripts/build.sh              # no args -> show menu and prompt
#
# Options (number or name, both work):
#   1 | backend    Build & deploy BACKEND        (Java code changed)
#   2 | frontend   Build & deploy FRONTEND       (React code changed)
#   3 | both       Build & deploy BOTH
#   4 | env        Recreate containers, NO build (.env changed only — seconds)
#   5 | restart    Restart containers            (no build, no recreate)
#   6 | status     Show container status
#   7 | logs       Follow logs [service: backend|frontend|db, default all]
#   8 | clean      Full --no-cache rebuild [service: backend|frontend|both]
#   9 | down       Stop & remove containers (volumes/DB PRESERVED)
#
# Cloud defaults to gcp. For AWS:  ./scripts/build.sh 1 aws
#
# Cheat sheet:  .java/.js changed -> 1/2/3 · .env changed -> 4 ·
#               weird build state -> 8 · nothing works -> 6 then 7
#
# Notes:
#  - Never runs `docker system prune`: that wipes the BuildKit layer cache and
#    is what turns 3-minute builds into 13-minute builds. Cache is preserved.
#  - Requires docker compose v2 (the GCP overlay uses !override syntax).
#  - Run from the repo root (the script cd's there itself).
# ============================================================================
set -euo pipefail

# --- resolve repo root (script lives in scripts/) ---
cd "$(dirname "$0")/.."

OPT="${1:-}"
CLOUD="${2:-gcp}"
SVC_ARG="${3:-}"

# allow "./build.sh 7 backend" (service as 2nd arg when it's not a cloud)
if [[ "$CLOUD" != "gcp" && "$CLOUD" != "aws" ]]; then
  SVC_ARG="$CLOUD"; CLOUD="gcp"
fi

PROJECT="taskmanager"
BASE="docker-compose.yml"
case "$CLOUD" in
  gcp) OVERLAY="deployments/gcp/compose/docker-compose.gcp.yml" ;;
  aws) OVERLAY="deployments/aws/compose/docker-compose.aws.yml" ;;
esac

# Compose v2 detection: prefer the CLI plugin; accept a standalone
# docker-compose binary IF it is v2 (identical syntax incl. !override).
if docker compose version >/dev/null 2>&1; then
  DC=(docker compose)
elif docker-compose version 2>/dev/null | grep -q " v2\."; then
  DC=(docker-compose)
else
  echo "❌ Docker Compose v2 not found (plugin or standalone). The overlays need v2."; exit 1
fi

COMPOSE() { "${DC[@]}" -p "$PROJECT" -f "$BASE" -f "$OVERLAY" "$@"; }

svc_name() {  # map short names to service names
  case "$1" in
    backend|be|1)  echo "taskmanager-backend" ;;
    frontend|fe|2) echo "taskmanager-frontend" ;;
    db)            echo "taskmanager-db" ;;
    both|all|3)    echo "taskmanager-backend taskmanager-frontend" ;;
    *)             echo "" ;;
  esac
}

banner() { echo; echo "════ $1  [cloud: $CLOUD] ════"; }
timed()  { local t0=$SECONDS; "$@"; echo "⏱  done in $(( SECONDS - t0 ))s"; }

menu() {
  cat <<'EOF'
  1) backend   - build & deploy backend   (Java changed)
  2) frontend  - build & deploy frontend  (React changed)
  3) both      - build & deploy both
  4) env       - recreate, no build       (.env changed - seconds)
  5) restart   - restart containers
  6) status    - container status
  7) logs      - follow logs [backend|frontend|db]
  8) clean     - full no-cache rebuild [backend|frontend|both]
  9) down      - stop & remove (DB volumes preserved)
EOF
  read -rp "Choose option: " OPT
}

[[ -z "$OPT" ]] && menu

case "$OPT" in
  1|backend)
    banner "BUILD BACKEND"
    timed COMPOSE up -d --build taskmanager-backend
    echo "✅ Backend deployed. Logs: ./scripts/build.sh 7 backend"
    ;;
  2|frontend)
    banner "BUILD FRONTEND"
    timed COMPOSE up -d --build taskmanager-frontend
    echo "✅ Frontend deployed. Now HARD-REFRESH the browser (Ctrl+Shift+R)."
    ;;
  3|both)
    banner "BUILD BOTH"
    timed COMPOSE up -d --build taskmanager-backend taskmanager-frontend
    echo "✅ Both deployed. Hard-refresh the browser for the frontend."
    ;;
  4|env)
    banner "ENV RECREATE (no build)"
    timed COMPOSE up -d
    echo "✅ Recreated with current .env. Verify: docker exec taskmanager-backend env | grep -E 'AI_|DIGEST_'"
    ;;
  5|restart)
    banner "RESTART"
    COMPOSE restart
    ;;
  6|status)
    banner "STATUS"
    COMPOSE ps
    ;;
  7|logs)
    banner "LOGS (Ctrl+C to exit)"
    S="$(svc_name "${SVC_ARG:-x}")"
    if [[ -n "$S" ]]; then COMPOSE logs -f --tail=100 $S
    else COMPOSE logs -f --tail=100; fi
    ;;
  8|clean)
    S="$(svc_name "${SVC_ARG:-both}")"
    [[ -z "$S" ]] && { echo "Service? backend|frontend|both"; exit 2; }
    banner "CLEAN REBUILD (--no-cache): $S"
    COMPOSE stop $S || true
    COMPOSE rm -f $S || true
    timed COMPOSE build --no-cache $S
    COMPOSE up -d $S
    echo "✅ Clean rebuild done (builder cache for OTHER services preserved)."
    ;;
  9|down)
    banner "DOWN (volumes preserved)"
    COMPOSE down --remove-orphans
    echo "🔒 Containers removed; DB data kept. Bring back: ./scripts/build.sh 4"
    ;;
  *)
    echo "Unknown option: $OPT (run with no args for the menu)"; exit 2 ;;
esac
