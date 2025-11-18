#!/bin/bash

# -----------------------------------------------------------------------------
# Replace GCP subdomain with AWS subdomain across all files in taskmanager/
# - Skips the .git directory
# - Prints each updated file
# - Shows a final summary and git status (if in a git repo)
# -----------------------------------------------------------------------------

set -e

# Resolve the directory of this script, then set TARGET_DIR to repo root
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
TARGET_DIR="$(cd "$SCRIPT_DIR/.." && pwd)"

OLD_DOMAIN="taskmanager.gcp.sriinfosoft.com"
NEW_DOMAIN="taskmanager.sriinfosoft.com"

echo "Updating domains in $TARGET_DIR ..."
echo "Replacing: $OLD_DOMAIN  -->  $NEW_DOMAIN"
echo "Skipping .git directory"

# Find all files that actually contain the OLD_DOMAIN, excluding .git
mapfile -t files < <(grep -rlI --exclude-dir=".git" "$OLD_DOMAIN" "$TARGET_DIR" || true)

count=${#files[@]}

if [ "$count" -eq 0 ]; then
  echo "✔ Done. Updated 0 file(s). No occurrences of '$OLD_DOMAIN' found."
  exit 0
fi

# Replace in each matched file
for file in "${files[@]}"; do
  sed -i "s/$OLD_DOMAIN/$NEW_DOMAIN/g" "$file"
  echo "Updated: $file"
done

echo "✔ Done. Updated $count file(s)."

# If this is a git repo, show a concise status
if [ -d "$TARGET_DIR/.git" ]; then
  echo
  echo "Git status (short):"
  (cd "$TARGET_DIR" && git status --short)
fi

