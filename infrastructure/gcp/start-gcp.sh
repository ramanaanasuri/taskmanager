#!/bin/bash
# ============================================================
# start-gcp.sh - Start Task Manager GCE instance
#
# GCP uses a STATIC regional IP so no need to update DNS
# after start -- the IP never changes unlike AWS.
#
# Usage: bash infrastructure/gcp/start-gcp.sh
# Requires: gcloud CLI authenticated
# ============================================================

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
CONFIG_FILE="$SCRIPT_DIR/.gcp-config"
[ -f "$CONFIG_FILE" ] && source "$CONFIG_FILE"

INSTANCE_NAME="${GCP_INSTANCE_NAME:-gce-for-fullstack-apps-learning}"
ZONE="${GCP_ZONE:-us-west1-b}"
PROJECT="${GCP_PROJECT_ID:-}"
SSH_USER="${GCP_SSH_USER:-ranasuri}"
FRONTEND_URL="${GCP_FRONTEND_URL:-https://taskmanager.gcp.sriinfosoft.com}"
BACKEND_URL="${GCP_BACKEND_URL:-https://api-taskmanager.gcp.sriinfosoft.com}"

if [ -z "$PROJECT" ]; then
  echo "ERROR: GCP_PROJECT_ID not set in .gcp-config"
  exit 1
fi

echo "============================================"
echo "Starting Task Manager GCP Instance"
echo "Instance: $INSTANCE_NAME | Zone: $ZONE"
echo "============================================"

# Check current status
STATUS=$(gcloud compute instances describe "$INSTANCE_NAME" \
  --zone="$ZONE" \
  --project="$PROJECT" \
  --format="value(status)" 2>/dev/null || echo "NOT_FOUND")

if [ "$STATUS" = "RUNNING" ]; then
  echo "Instance already running."
elif [ "$STATUS" = "TERMINATED" ]; then
  echo "Starting instance..."
  gcloud compute instances start "$INSTANCE_NAME" \
    --zone="$ZONE" \
    --project="$PROJECT"
  echo "Waiting for instance to be ready..."
  gcloud compute instances wait-until-status "$INSTANCE_NAME" \
    --zone="$ZONE" \
    --project="$PROJECT" \
    --status=RUNNING 2>/dev/null || sleep 30
else
  echo "ERROR: Instance status is '$STATUS'. Cannot start."
  exit 1
fi

# Get instance IP
INSTANCE_IP=$(gcloud compute instances describe "$INSTANCE_NAME" \
  --zone="$ZONE" \
  --project="$PROJECT" \
  --format="value(networkInterfaces[0].accessConfigs[0].natIP)")

echo ""
echo "============================================"
echo "Instance is UP"
echo "IP:       $INSTANCE_IP (static -- DNS unchanged)"
echo "SSH:      gcloud compute ssh ${SSH_USER}@${INSTANCE_NAME} --zone=${ZONE}"
echo "App:      $FRONTEND_URL"
echo "API:      $BACKEND_URL"
echo "Note:     No CloudFront update needed -- GCP uses static IP"
echo "============================================"
