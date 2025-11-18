#!/bin/bash

# -----------------------------------------------------------------------------
# Replace GCP subdomain with AWS subdomain across all files in taskmanager/
# - Skips the .git directory
# - Skips this script (scripts/set_aws_env.sh)
# - Prints each updated file
# - Shows a final summary and git status (if in a git repo)
# -----------------------------------------------------------------------------

set -e

# Resolve script path and repo root
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
SCRIPT_NAME="$(basename "${BASH_SOURCE[0]}")"
SCRIPT_PATH="$SCRIPT_DIR/$SCRIPT_NAME"

TARGET_DIR="$(cd "$SCRIPT_DIR/.." && pwd)"

OLD_DOMAIN="taskmanager.gcp.sriinfosoft.com"
NEW_DOMAIN="taskmanager.sriinfosoft.com"

echo "Updating domains in $TARGET_DIR ..."
echo "Replacing: $OLD_DOMAIN  -->  $NEW_DOMAIN"
echo "Skipping .git directory and $SCRIPT_PATH"

# Find all files that contain OLD_DOMAIN, excluding .git and this script
mapfile -t files < <(grep -rlI \
  --exclude-dir=".git" \
  --exclude="$SCRIPT_NAME" \
  "$OLD_DOMAIN" "$TARGET_DIR" || true)

count=${#files[@]}

if [ "$count" -eq 0 ]; then
  echo "✔ Done. Updated 0 file(s). No occurrences of '$OLD_DOMAIN' found (outside excluded paths)."
  exit 0
fi

# Replace in each matched file (extra safety: skip this script if it ever appears)
for file in "${files[@]}"; do
  if [[ "$file" == "$SCRIPT_PATH" ]]; then
    continue
  fi

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

