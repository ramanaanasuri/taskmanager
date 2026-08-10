#!/usr/bin/env bash
set -euo pipefail

CLOUD="${1:-gcp}"            # gcp | aws
CMD="${2:-}"                 # up | down | ps | logs
EXTRA="${3:-}"               # optional flags like --rebuild or service names

# --- CONFIG ---
PROJECT_NAME="taskmanager"   # keep consistent so ps sees the same stack
BASE_FILE="docker-compose.yml"
case "$CLOUD" in
  gcp)
    OVERLAY="deployments/gcp/compose/docker-compose.gcp.yml"
    ;;
  aws)
    OVERLAY="deployments/aws/compose/docker-compose.aws.yml"
    ;;
  *)
    echo "Unknown cloud: $CLOUD (use gcp|aws)" >&2; exit 2;;
esac

COMPOSE() {
  docker compose \
    -p "$PROJECT_NAME" \
    -f "$BASE_FILE" \
    -f "$OVERLAY" \
    "$@"
}

# Helper: show which files/project we’re using
show_ctx() {
  echo "➡️ Using project: $PROJECT_NAME"
  echo "➡️ Files:"
  echo "   - $BASE_FILE"
  echo "   - $OVERLAY"
  echo "➡️ Services in merged config:"
  COMPOSE config --services
}

case "$CMD" in
  up)
    show_ctx
    if [[ "$EXTRA" == "--rebuild" ]]; then
      shift 3 || true
      COMPOSE up -d --build
    else
      COMPOSE up -d
    fi
    ;;

  down)
  show_ctx
  # Safe by default: keep named volumes (DB data).
  # Use: scripts/stack.sh down --wipe   # to also remove volumes (DESTROYS DB DATA)
  if [[ "${2:-}" == "--wipe" || "${2:-}" == "--volumes" ]]; then
    echo "⚠️  Removing containers AND VOLUMES (DB data will be deleted)…"
    COMPOSE down --remove-orphans -v || true
  else
    echo "🔒 Removing containers (volumes preserved)…"
    COMPOSE down --remove-orphans || true
  fi
  ;;


  ps|"")
    show_ctx
    echo "➡️ docker compose ps (merged files)"
    COMPOSE ps || true
    echo
    echo "➡️ fallback: docker ps (name filter)"
    docker ps --format '{{.Names}}\t{{.Status}}\t{{.Ports}}' | grep -E 'taskmanager-(db|backend|frontend)' || true
    ;;

  logs)
    show_ctx
    COMPOSE logs -f --tail=200 taskmanager-db taskmanager-backend taskmanager-frontend
    ;;

  stop)
  show_ctx
  COMPOSE stop || true
  ;;

  start)
  show_ctx
  COMPOSE start || true
  ;;


  *)
    echo "Usage: scripts/stack.sh <gcp|aws> <up|down|start|stop|ps|logs> [--rebuild]" >&2
    exit 2;;
esac

