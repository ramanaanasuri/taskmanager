#!/bin/bash

# -----------------------------------------------------------------------------
# Replace GCP subdomain with AWS subdomain across all files in taskmanager/
# Skips the .git directory
# Provides a clean summary count, no per-file printing
# -----------------------------------------------------------------------------

TARGET_DIR="../taskmanager"
OLD_DOMAIN="taskmanager.gcp.sriinfosoft.com"
NEW_DOMAIN="taskmanager.sriinfosoft.com"

echo "Updating domains in $TARGET_DIR ..."
echo "Replacing: $OLD_DOMAIN  -->  $NEW_DOMAIN"
echo "Skipping .git directory"

count=0

# Find all files except those under .git and replace text
find "$TARGET_DIR" \
  -path "$TARGET_DIR/.git" -prune -o \
  -type f -print0 | while IFS= read -r -d '' file; do
    if grep -q "$OLD_DOMAIN" "$file"; then
        sed -i "s/$OLD_DOMAIN/$NEW_DOMAIN/g" "$file"
        count=$((count + 1))
    fi
done

echo "✔ Done. Updated $count file(s)."

