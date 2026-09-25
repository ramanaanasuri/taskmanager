#!/usr/bin/env bash
#
# kb-sources.sh — choose what the Mentor Agent (InsightHub) grounds its answers in.
#
#   ./scripts/kb-sources.sh          invest pages + all KB docs, read from the LIVE site catalogs
#   ./scripts/kb-sources.sh --off    back to the default (invest catalog only)
#
# Writes KB_INVEST_URLS into .env (backup: .env.bak.<timestamp>, git-ignored). Nothing secret
# is read or printed. Re-run whenever KB docs are added, then ./scripts/build.sh 4.
#
set -euo pipefail
cd "$(dirname "$0")/.."

INVEST_BASE="${INVEST_BASE:-https://sriinfosoft.com/invest/}"
KB_BASE="${KB_BASE:-https://sriinfosoft.com/kb/}"

[ -f .env ] || { echo "ERROR: no .env in $(pwd)"; exit 1; }
grep -q 'KB_INVEST_URLS' docker-compose.yml || {
  echo "ERROR: docker-compose.yml does not pass KB_INVEST_URLS to the backend, so .env would be ignored."
  echo "       Add this line under taskmanager-backend -> environment (after AI_MODEL):"
  echo "         KB_INVEST_URLS: \${KB_INVEST_URLS:-}"
  exit 1
}

if [ "${1:-}" = "--off" ]; then
  LINE=""
else
  LINE="$(python3 - "$INVEST_BASE" "$KB_BASE" <<'PY'
import json, sys, urllib.request
urls = []
for base in sys.argv[1:]:
    entries = json.load(urllib.request.urlopen(base + "catalog.json", timeout=20))
    paths = [e["path"] for e in entries if e.get("path")]
    print(f"  {base}catalog.json: {len(paths)} doc(s)", file=sys.stderr)
    urls += [p if p.startswith("http") else base + p for p in paths]
print("KB_INVEST_URLS=" + ",".join(urls))
PY
)" || { echo "ERROR: could not read the site catalogs (.env unchanged)"; exit 1; }
fi

cp -p .env ".env.bak.$(date +%Y%m%d-%H%M%S)"
TMP="$(mktemp .env.XXXXXX)"
grep -v '^KB_INVEST_URLS=' .env > "$TMP" || true
[ -n "$LINE" ] && echo "$LINE" >> "$TMP"
chmod --reference=.env "$TMP" 2>/dev/null || true
mv "$TMP" .env

if [ -n "$LINE" ]; then
  echo "✅ .env: KB_INVEST_URLS set ($(echo "$LINE" | tr ',' '\n' | wc -l) pages)"
else
  echo "✅ .env: KB_INVEST_URLS removed (invest catalog only)"
fi
echo "Next: ./scripts/build.sh 4   (recreates the backend; the KB cache reloads on start)"
