echo "Discard all tracked file changes"
git reset --hard HEAD
echo "Remove all untracked files and folders"
git clean -fdn
git fetch origin
git pull origin feature/notifications-clean
./scripts/set_aws_env.sh
