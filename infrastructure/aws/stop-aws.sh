#!/bin/bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
CONFIG_FILE="$SCRIPT_DIR/.aws-config"
[ -f "$CONFIG_FILE" ] && source "$CONFIG_FILE"

INSTANCE_ID="${TASKMANAGER_INSTANCE_ID:-}"
REGION="${AWS_DEFAULT_REGION:-us-west-1}"

[ -z "$INSTANCE_ID" ] && { echo "ERROR: Set TASKMANAGER_INSTANCE_ID in .aws-config"; exit 1; }

STATE=$(aws ec2 describe-instances --instance-ids "$INSTANCE_ID" --region "$REGION" --query 'Reservations[0].Instances[0].State.Name' --output text)

if [ "$STATE" = "stopped" ]; then
  echo "Instance already stopped."
  exit 0
fi

echo "Stopping instance $INSTANCE_ID..."
aws ec2 stop-instances --instance-ids "$INSTANCE_ID" --region "$REGION" > /dev/null
aws ec2 wait instance-stopped --instance-ids "$INSTANCE_ID" --region "$REGION"

echo "============================================"
echo "Instance stopped."
echo "Still billing: EBS (~\$2.30/mo) + Public IPv4 (~\$3.36/mo)"
echo "Compute billing: STOPPED"
echo "To restart: ./scripts/start-aws.sh"
echo "============================================"
