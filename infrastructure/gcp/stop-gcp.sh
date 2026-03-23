#!/bin/bash
# ============================================================
# stop-gcp.sh - Stop Task Manager GCE instance
#
# Stops compute billing. Static IP is free when instance
# is stopped on GCP (unlike AWS which charges for IPv4).
#
# Usage: bash infrastructure/gcp/stop-gcp.sh
# ============================================================

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
CONFIG_FILE="$SCRIPT_DIR/.gcp-config"
[ -f "$CONFIG_FILE" ] && source "$CONFIG_FILE"

INSTANCE_NAME="${GCP_INSTANCE_NAME:-gce-for-fullstack-apps-learning}"
ZONE="${GCP_ZONE:-us-west1-b}"
PROJECT="${GCP_PROJECT_ID:-}"

if [ -z "$PROJECT" ]; then
  echo "ERROR: GCP_PROJECT_ID not set in .gcp-config"
  exit 1
fi

STATUS=$(gcloud compute instances describe "$INSTANCE_NAME" \
  --zone="$ZONE" \
  --project="$PROJECT" \
  --format="value(status)" 2>/dev/null || echo "NOT_FOUND")

if [ "$STATUS" = "TERMINATED" ]; then
  echo "Instance already stopped."
  exit 0
fi

echo "Stopping instance $INSTANCE_NAME..."
gcloud compute instances stop "$INSTANCE_NAME" \
  --zone="$ZONE" \
  --project="$PROJECT"

echo "============================================"
echo "Instance stopped."
echo "GCP static IP retained at no cost."
echo "Compute billing: STOPPED"
echo "To restart: bash infrastructure/gcp/start-gcp.sh"
echo "============================================"
