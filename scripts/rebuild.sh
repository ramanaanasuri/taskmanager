#!/usr/bin/env bash
# rebuild.sh — stop, clean, rebuild (no cache), restart
# Usage:
#   ./rebuild.sh                 # rebuilds taskmanager-frontend only
#   ./rebuild.sh backend         # rebuilds taskmanager-backend only
#   ./rebuild.sh all             # rebuilds ALL services
#   COMPOSE_FILE=docker-compose.prod.yml ./rebuild.sh frontend   # if you use a different compose file

set -euo pipefail

SERVICE="${1:-frontend}"                 # frontend | backend | all
DEFAULT_FRONTEND="taskmanager-frontend"  # adjust if your service name differs
DEFAULT_BACKEND="taskmanager-backend"

# Compose wrapper (supports both docker-compose and docker compose)
compose() {
  if command -v docker-compose >/dev/null 2>&1; then
    docker-compose "$@"
  else
    docker compose "$@"
  fi
}

case "$SERVICE" in
  front|fe|frontend)
    SVC="$DEFAULT_FRONTEND"
    echo "==> Rebuild ONE: $SVC"
    compose stop "$SVC" || true
    compose rm -f "$SVC" || true
    compose build --no-cache "$SVC"
    compose up -d --no-deps --force-recreate "$SVC"
    ;;

  back|be|backend)
    SVC="$DEFAULT_BACKEND"
    echo "==> Rebuild ONE: $SVC"
    compose stop "$SVC" || true
    compose rm -f "$SVC" || true
    compose build --no-cache "$SVC"
    compose up -d --no-deps --force-recreate "$SVC"
    ;;

  all|stack|*)
    if [[ "$SERVICE" == "all" || "$SERVICE" == "stack" ]]; then
      echo "==> Rebuild ALL services"
      compose down
      compose build --no-cache
      compose up -d
    else
      # Unknown token -> treat as a literal service name
      SVC="$SERVICE"
      echo "==> Rebuild ONE: $SVC"
      compose stop "$SVC" || true
      compose rm -f "$SVC" || true
      compose build --no-cache "$SVC"
      compose up -d --no-deps --force-recreate "$SVC"
    fi
    ;;
esac

# Optional cleanup of dangling images/containers/networks (safe):
docker system prune -f >/dev/null 2>&1 || true

echo
echo "✅ Done."
compose ps

