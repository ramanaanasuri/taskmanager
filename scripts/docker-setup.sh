#!/usr/bin/env bash
set -euo pipefail

echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
echo " Docker Compose Setup & Compatibility Checker"
echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"

# 1. Verify docker installation
if ! command -v docker &>/dev/null; then
  echo "❌ Docker not found. Please install Docker first."
  exit 1
fi

# 2. Check for compose plugin
if docker compose version &>/dev/null; then
  echo "✅ 'docker compose' plugin is already available."
  docker compose version
else
  echo "⚠️  'docker compose' (plugin) not found."
fi

# 3. Check for legacy docker-compose binary
if docker-compose version &>/dev/null; then
  echo "✅ 'docker-compose' binary found."
  docker-compose version
else
  echo "⚠️  'docker-compose' binary not found."
fi

# 4. Create wrapper if docker-compose works but docker compose doesn't
if ! docker compose version &>/dev/null && docker-compose version &>/dev/null; then
  echo "🛠  Creating safe wrapper so 'docker compose' works like 'docker-compose'..."

  sudo tee /usr/local/bin/docker-compose-wrapper >/dev/null <<'EOF'
#!/usr/bin/env bash
docker-compose "$@"
EOF

  sudo chmod +x /usr/local/bin/docker-compose-wrapper

  # remove existing symlink if exists
  if [[ -L /usr/local/bin/docker ]]; then
    echo "Removing old symlink: /usr/local/bin/docker"
    sudo rm -f /usr/local/bin/docker
  fi

  # create new symlink
  sudo ln -s /usr/local/bin/docker-compose-wrapper /usr/local/bin/docker
  echo "✅ Wrapper created successfully!"
fi

# 5. Verify results
echo
echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
echo " Verifying both commands..."
echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
{
  echo "- docker compose version:"
  docker compose version || echo "⚠️  docker compose still unavailable."
  echo
  echo "- docker-compose version:"
  docker-compose version || echo "⚠️  docker-compose unavailable."
} | sed 's/^/    /'

echo
echo "✅ Setup complete."
echo "You can now use either 'docker compose' or 'docker-compose'."
echo "To remove this setup, run:"
echo "  sudo rm /usr/local/bin/docker-compose-wrapper /usr/local/bin/docker"
echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"

