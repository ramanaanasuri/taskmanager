#!/bin/bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
CONFIG_FILE="$SCRIPT_DIR/../infrastructure/aws/.aws-config"
[ -f "$CONFIG_FILE" ] && source "$CONFIG_FILE"

INSTANCE_ID="${TASKMANAGER_INSTANCE_ID:-}"
REGION="${AWS_DEFAULT_REGION:-us-west-1}"
FRONTEND_DIST_ID="${TASKMANAGER_FRONTEND_DIST_ID:-}"
BACKEND_DIST_ID="${TASKMANAGER_BACKEND_DIST_ID:-}"

[ -z "$INSTANCE_ID" ] && { echo "ERROR: Set TASKMANAGER_INSTANCE_ID in .aws-config"; exit 1; }

echo "Starting instance $INSTANCE_ID..."
STATE=$(aws ec2 describe-instances --instance-ids "$INSTANCE_ID" --region "$REGION" --query 'Reservations[0].Instances[0].State.Name' --output text)

if [ "$STATE" = "running" ]; then
  echo "Already running."
else
  aws ec2 start-instances --instance-ids "$INSTANCE_ID" --region "$REGION" > /dev/null
fi

aws ec2 wait instance-running --instance-ids "$INSTANCE_ID" --region "$REGION"

PUBLIC_DNS=""
for i in $(seq 1 30); do
  PUBLIC_DNS=$(aws ec2 describe-instances --instance-ids "$INSTANCE_ID" --region "$REGION" --query 'Reservations[0].Instances[0].PublicDnsName' --output text)
  [ -n "$PUBLIC_DNS" ] && [ "$PUBLIC_DNS" != "None" ] && break
  echo "  Waiting for public DNS... attempt $i/30"
  sleep 5
done

PUBLIC_IP=$(aws ec2 describe-instances --instance-ids "$INSTANCE_ID" --region "$REGION" --query 'Reservations[0].Instances[0].PublicIpAddress' --output text)
echo "Instance running: $PUBLIC_DNS ($PUBLIC_IP)"

update_cf() {
  local DIST_ID="$1" NEW_DOMAIN="$2" LABEL="$3"
  [ -z "$DIST_ID" ] && { echo "WARNING: $LABEL dist ID not set -- skipping"; return; }
  echo "Updating $LABEL CloudFront origin -> $NEW_DOMAIN..."
  ETAG=$(aws cloudfront get-distribution-config --id "$DIST_ID" --query 'ETag' --output text)
  TMPFILE=$(mktemp)
  aws cloudfront get-distribution-config --id "$DIST_ID" --query 'DistributionConfig' > "$TMPFILE"
  python3 -c "
import sys,json
with open('$TMPFILE') as f: c=json.load(f)
for o in c['Origins']['Items']: o['DomainName']='$NEW_DOMAIN'
print(json.dumps(c))" | aws cloudfront update-distribution \
    --id "$DIST_ID" \
    --if-match "$ETAG" \
    --distribution-config file:///dev/stdin > /dev/null
  rm -f "$TMPFILE"
  echo "$LABEL origin updated."
}

update_cf "$FRONTEND_DIST_ID" "$PUBLIC_DNS" "Frontend"
update_cf "$BACKEND_DIST_ID"  "$PUBLIC_DNS" "Backend"

echo "============================================"
echo "Instance IP:  $PUBLIC_IP"
echo "SSH:          ssh -i ~/.ssh/ranasuri-manteca.pem ec2-user@$PUBLIC_IP"
echo "App:          https://taskmanager.sriinfosoft.com"
echo "API:          https://api-taskmanager.sriinfosoft.com"
echo "Note:         CloudFront propagation takes 5-15 min"
echo "============================================"
