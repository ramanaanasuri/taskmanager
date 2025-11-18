#!/bin/bash

# Quick git push script
# Usage: ./scripts/quick_git_push.sh "commit message" "branch-name"

if [ $# -ne 2 ]; then
    echo "Usage: $0 \"commit message\" \"branch-name\""
    echo "Example: $0 \"backup script for aws env\" \"feature/notifications-clean\""
    exit 1
fi

COMMIT_MSG="$1"
BRANCH="$2"

echo "Executing git commands..."
echo "Commit message: $COMMIT_MSG"
echo "Branch: $BRANCH"
echo ""

# Add all changes
echo "Running: git add -A"
git add -A

# Commit with message
echo "Running: git commit -m \"$COMMIT_MSG\""
git commit -m "$COMMIT_MSG"

# Push to origin branch
echo "Running: git push origin $BRANCH"
git push origin "$BRANCH"

echo ""
echo "Done!"
