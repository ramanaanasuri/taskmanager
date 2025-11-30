#!/usr/bin/env bash
set -euo pipefail

# ===== CONFIG =====
REPO_DIR="/home/ec2-user/sriinfo/taskmanager" # change to your repo path
BRANCH="${1:-feature/notifications-clean}"    # default branch, override by arg
REMOTE="origin"
REPLACE_SCRIPT="$REPO_DIR/scripts/set_aws_env.sh"  # your script
# ==================

echo ">>> Updating repo in: $REPO_DIR"
cd "$REPO_DIR"

echo ">>> Fetching latest from $REMOTE..."
git fetch "$REMOTE"

echo ">>> Checking out branch: $BRANCH"
git checkout "$BRANCH"

echo ">>> Resetting hard to $REMOTE/$BRANCH (dropping local changes)..."
git reset --hard "$REMOTE/$BRANCH"

echo ">>> Cleaning untracked files..."
git clean -fd

echo ">>> Current status:"
git status

# Optional: run your AWS-specific string replacement
if [ -x "$REPLACE_SCRIPT" ]; then
  echo ">>> Running AWS replace script: $REPLACE_SCRIPT"
  "$REPLACE_SCRIPT"
else
  echo ">>> Skip replace script: $REPLACE_SCRIPT not found or not executable"
fi

echo ">>> Done."

