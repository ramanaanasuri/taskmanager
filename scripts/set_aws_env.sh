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

count=0

# Walk all files under TARGET_DIR (excluding .git), check and replace
while IFS= read -r -d '' file; do
  # Extra safety: never touch this script itself
  if [[ "$file" == "$SCRIPT_PATH" ]]; then
    continue
  fi

  if grep -q "$OLD_DOMAIN" "$file"; then
    sed -i "s/$OLD_DOMAIN/$NEW_DOMAIN/g" "$file"
    echo "Updated: $file"
    count=$((count + 1))
  fi
done < <(find "$TARGET_DIR" \
           -type f \
           ! -path "$TARGET_DIR/.git/*" \
           -print0)

echo "✔ Done. Updated $count file(s)."

# If this is a git repo, show a concise status
if [ -d "$TARGET_DIR/.git" ]; then
  echo
  echo "Git status (short):"
  (cd "$TARGET_DIR" && git status --short)
fi

