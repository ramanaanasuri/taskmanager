#!/usr/bin/env bash
# ============================================================================
# dev-redeploy.sh — FAST backend redeploy for debugging (no Docker image build)
#
# Compiles the jar in a throwaway Maven container (host has no mvn), hot-swaps
# it into the running backend container, and restarts just the JVM.
# ~1-3 min vs ~14 min for a full `build.sh 1`. First run downloads deps into a
# persistent named volume (mvn-repo-cache); subsequent runs reuse it and are fast.
#
# Use for rapid Java debug iterations. It does NOT rebuild the Docker image —
# the container keeps its image and just runs the new jar. Run the real
# `./scripts/build.sh 1` for the final committed change (this is a dev shortcut).
#
# Usage:   ./scripts/dev-redeploy.sh
#          ./scripts/dev-redeploy.sh --logs     # tail [KB]/[Mentor] logs after
#
# Safe: nothing permanent changes. Next `build.sh 1` rebuilds the image from
# source, replacing the hot-swapped jar.
# ============================================================================
set -euo pipefail
cd "$(dirname "$0")/.."          # repo root

CONTAINER="taskmanager-backend"
BACKEND_DIR="$PWD/apps/backend"
JAR="apps/backend/target/taskmanager-1.0.0.jar"
MVN_IMAGE="maven:3.9-eclipse-temurin-17"
MVN_CACHE_VOL="mvn-repo-cache"

echo "════ DEV REDEPLOY (container compile + hot-swap) ════"

# 1) Compile in a throwaway Maven container. Mount the source and a persistent
#    ~/.m2 cache volume so deps download only once, ever.
echo "▶ compiling in $MVN_IMAGE (offline deps cached in volume $MVN_CACHE_VOL)…"
docker run --rm \
  -v "$BACKEND_DIR":/app \
  -v "$MVN_CACHE_VOL":/root/.m2 \
  -w /app \
  "$MVN_IMAGE" \
  mvn -q package -DskipTests

[ -f "$JAR" ] || { echo "✗ jar not found at $JAR — compile failed?"; exit 1; }

# 2) Swap the jar into the running container
echo "▶ copying jar into $CONTAINER:/app/app.jar…"
docker cp "$JAR" "$CONTAINER:/app/app.jar"

# 3) Restart just the JVM (DB + other containers untouched)
echo "▶ restarting $CONTAINER…"
docker restart "$CONTAINER" >/dev/null

# 4) Wait for health
echo -n "▶ waiting for healthy"
for i in $(seq 1 30); do
  sleep 2; echo -n "."
  if curl -sf http://localhost:8080/actuator/health >/dev/null 2>&1; then
    echo " UP"; break
  fi
done
echo "✓ done"

if [ "${1:-}" = "--logs" ]; then
  echo "════ tailing [KB]/[Mentor] logs (Ctrl-C to stop) ════"
  docker logs -f "$CONTAINER" --since 10s 2>&1 | grep --line-buffered -E "\[KB\]|\[Mentor\]"
fi
