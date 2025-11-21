#!/usr/bin/env bash
# scripts/check_deployed_string.sh
#
# Usage:
#   ./scripts/check_deployed_string.sh <container-name-filter> <search-string> [base-path]
#
# Examples:
#   ./scripts/check_deployed_string.sh taskmanager-frontend "[TM_CHECK] App.js deployed marker v1"
#   ./scripts/check_deployed_string.sh taskmanager-backend "New log line text" /app

set -euo pipefail

if [[ $# -lt 2 ]]; then
  echo "Usage: $0 <container-name-filter> <search-string> [base-path]" >&2
  exit 1
fi

CONTAINER_FILTER="$1"
SEARCH_STRING="$2"
BASE_PATH="${3:-/usr/share/nginx/html}"

# 1) Find the running container by name filter (same style you already use)
CID="$(docker ps -qf "name=${CONTAINER_FILTER}" || true)"

if [[ -z "$CID" ]]; then
  echo "❌ No running container found matching name filter: ${CONTAINER_FILTER}" >&2
  docker ps --format '  {{.ID}}  {{.Names}}'
  exit 1
fi

echo "🐳 Using container: ${CID} (filter='${CONTAINER_FILTER}')"
echo "📂 Base path in container: ${BASE_PATH}"
echo "🔎 Searching for: ${SEARCH_STRING}"
echo

# 2) Exec into the container and grep recursively
docker exec \
  -e SEARCH_STRING="$SEARCH_STRING" \
  -e BASE_PATH="$BASE_PATH" \
  -it "$CID" sh -lc '
  echo "================ SEARCH RESULTS ================"
  if grep -RIn --color=always "$SEARCH_STRING" "$BASE_PATH" 2>/dev/null; then
    echo
    echo "✅ Match(es) found above."
  else
    echo "⚠️  No matches found for: $SEARCH_STRING"
  fi
  echo "==============================================="
'

