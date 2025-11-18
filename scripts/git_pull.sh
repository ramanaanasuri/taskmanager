#!/bin/bash

# Quick git pull script
# Usage: ./scripts/quick_git_pull.sh "branch-name"

if [ $# -ne 1 ]; then
    echo "Usage: $0 \"branch-name\""
    echo "Example: $0 \"feature/notifications-clean\""
    exit 1
fi

BRANCH="$1"

echo "Executing git pull..."
echo "Branch: $BRANCH"
echo ""

# Pull from origin branch
echo "Running: git pull origin $BRANCH"
git pull origin "$BRANCH"

echo ""
echo "Done!"
