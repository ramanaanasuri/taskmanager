cd ~/taskmanager

# If this directory is NOT a git repo yet:
# git init

# Make sure secrets are ignored
printf '\n# local secrets\n.env\n' >> .gitignore

# Optional: ignore backup artifacts
printf '\n# backups\n*.bak\n*.bak.*\n' >> .gitignore

# Commit everything
git add -A
git commit -m "Initial commit: taskmanager (apps, deployments, scripts)"

# Choose one remote style:

## SSH (recommended; you already use SSH)
git remote add origin git@github.com:ramanaanasuri/taskmanager.git

## -- OR -- HTTPS (will prompt for token on first push)
# git remote add origin https://github.com/ramanaanasuri/taskmanager.git

# Ensure your default branch name is 'main' (or keep 'master' if you prefer)
git branch -M main

# Push
git push -u origin main

