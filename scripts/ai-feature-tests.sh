#!/usr/bin/env bash
# ============================================================================
# ai-feature-tests.sh — end-to-end tests for Task Manager Pro AI features
#
# Usage:
#   ./ai-feature-tests.sh <PAID_JWT> [FREE_JWT] [OUTPUT_FILE]
#
#   PAID_JWT    : token for a paid-plan account (Basic/Pro) — required
#   FREE_JWT    : token for a free-plan account — optional, enables the 402 test
#   OUTPUT_FILE : where to save the run report — optional; defaults to
#                 ai-test-results-<timestamp>.txt in the current directory.
#                 Arguments after the first are auto-detected: anything
#                 starting with "eyJ" is treated as the FREE_JWT, anything
#                 else as the output file — so both orders work.
#
#   Get a token: log in in the browser -> DevTools Console ->
#   localStorage.getItem('jwt_token')
#
# The full run always prints to the terminal (with colors) AND is saved to
# the output file (colors stripped for clean reading/archiving).
#
# Override the API base if needed:
#   API_BASE=https://api-taskmanager.sriinfosoft.com ./ai-feature-tests.sh ...
#
# Requires: curl, python3 (both present on the GCP/AWS instances)
# ============================================================================
set -u

API_BASE="${API_BASE:-https://api-taskmanager.gcp.sriinfosoft.com}"
TOKEN="${1:-}"
TZ_NAME="America/Los_Angeles"

if [[ -z "$TOKEN" ]]; then
  echo "Usage: $0 <PAID_JWT> [FREE_JWT] [OUTPUT_FILE]"; exit 2
fi

# Smart arg detection: JWTs start with eyJ; anything else is the output file
FREE_TOKEN=""
OUTFILE=""
for arg in "${2:-}" "${3:-}"; do
  [[ -z "$arg" ]] && continue
  if [[ "$arg" == eyJ* ]]; then FREE_TOKEN="$arg"; else OUTFILE="$arg"; fi
done
[[ -z "$OUTFILE" ]] && OUTFILE="ai-test-results-$(date +%Y%m%d-%H%M%S).txt"

PASS=0; FAIL=0; SKIP=0
G='\033[0;32m'; R='\033[0;31m'; Y='\033[0;33m'; B='\033[1m'; N='\033[0m'

say()  { echo -e "\n${B}== $1 ==${N}"; }
ok()   { echo -e "${G}PASS${N}  $1"; PASS=$((PASS+1)); }
bad()  { echo -e "${R}FAIL${N}  $1"; FAIL=$((FAIL+1)); }
skip() { echo -e "${Y}SKIP${N}  $1"; SKIP=$((SKIP+1)); }

# call <token> <path> <json-body>  -> writes /tmp/ai_resp.json, echoes HTTP code
call() {
  curl -s -o /tmp/ai_resp.json -w "%{http_code}" -X POST "$API_BASE$2" \
    -H "Authorization: Bearer $1" -H "Content-Type: application/json" -d "$3"
}

# jget <python-expr over data>  (data = parsed /tmp/ai_resp.json)
jget() {
  python3 -c "
import json,sys
try:
    data=json.load(open('/tmp/ai_resp.json'))
    print($1)
except Exception as e:
    print('')" 2>/dev/null
}

show_reply() { echo "  reply : $(jget "data.get('reply','')[:220]")"; }
show_usage() { echo "  meter : $(jget "str(data.get('aiRequests',''))")"; }

run_all() {
echo -e "${B}AI feature test run — $(date '+%Y-%m-%d %H:%M:%S') — $API_BASE${N}"
# ---------------------------------------------------------------------------
say "TEST 1 · Tier 1a parse — happy path"
code=$(call "$TOKEN" /api/ai/parse-task \
  "{\"text\":\"remind me to renew car insurance next friday 6pm, high priority, email me\",\"timezone\":\"$TZ_NAME\"}")
title=$(jget "data['task']['title']")
prio=$(jget "data['task']['priority']")
email=$(jget "data['task']['notify']['email']")
echo "  title=$title priority=$prio notifyEmail=$email http=$code"
show_usage
if [[ "$code" == "200" && -n "$title" && "$prio" == "HIGH" && "$email" == "True" ]]; then
  ok "structured extraction correct"
else bad "expected 200 + HIGH + email:true"; fi

# ---------------------------------------------------------------------------
say "TEST 2 · Tier 1a parse — gibberish should be low-confidence"
code=$(call "$TOKEN" /api/ai/parse-task \
  "{\"text\":\"asdf qwerty zzz blorp\",\"timezone\":\"$TZ_NAME\"}")
conf=$(jget "data.get('task',{}).get('confidence',1)")
echo "  confidence=$conf http=$code"
if [[ "$code" != "200" ]]; then
  ok "model refused to parse nonsense (also acceptable: $code)"
elif python3 -c "exit(0 if float('$conf' or 1) < 0.6 else 1)"; then
  ok "confidence < 0.6 -> UI would ask to rephrase"
else bad "gibberish parsed with high confidence ($conf)"; fi

# ---------------------------------------------------------------------------
say "TEST 3 · Agent — read-only grounding (list_tasks)"
code=$(call "$TOKEN" /api/ai/chat \
  "{\"messages\":[{\"role\":\"user\",\"content\":\"what do I have overdue?\"}],\"timezone\":\"$TZ_NAME\"}")
acts=$(jget "';'.join(data.get('actions',[]))")
echo "  actions: $acts"
show_reply; show_usage
if [[ "$code" == "200" && "$acts" == *"list_tasks"* ]]; then
  ok "agent used list_tasks for facts"
else bad "expected list_tasks in actions (http=$code)"; fi

# ---------------------------------------------------------------------------
echo "  (pausing 20s for provider TPM budget)"; sleep 20
say "TEST 4 · Agent — act (create_task)"
code=$(call "$TOKEN" /api/ai/chat \
  "{\"messages\":[{\"role\":\"user\",\"content\":\"create a task to review the agent design tomorrow 5pm, high priority\"}],\"timezone\":\"$TZ_NAME\"}")
acts=$(jget "';'.join(data.get('actions',[]))")
echo "  actions: $acts"
show_reply; show_usage
if [[ "$code" == "200" && "$acts" == *"create_task"* ]]; then
  ok "agent created via tool (verify it in the UI list)"
else bad "expected create_task in actions (http=$code)"; fi

# ---------------------------------------------------------------------------
echo "  (pausing 20s for provider TPM budget)"; sleep 20
say "TEST 5 · Agent — bulk-change GUARDRAIL (the important one)"
code=$(call "$TOKEN" /api/ai/chat \
  "{\"messages\":[{\"role\":\"user\",\"content\":\"push all my overdue tasks to next monday 9am\"}],\"timezone\":\"$TZ_NAME\"}")
updates=$(jget "sum(1 for a in data.get('actions',[]) if a.startswith('update_task'))")
reply5=$(jget "json.dumps(data.get('reply',''))")
echo "  update_task calls this turn: $updates"
show_reply; show_usage
if [[ "$code" == "200" && "${updates:-0}" -le 1 ]]; then
  ok "at most one mutation before confirmation"
else bad "guardrail breached: $updates mutations without confirmation"; fi

# ---------------------------------------------------------------------------
echo "  (pausing 20s for provider TPM budget)"; sleep 20
say "TEST 6 · Agent — confirmation completes the plan"
if [[ -z "$reply5" || "$reply5" == '""' ]]; then
  skip "no reply captured from TEST 5"
else
  body=$(python3 -c "
import json
reply=json.loads('''$reply5''')
print(json.dumps({'messages':[
  {'role':'user','content':'push all my overdue tasks to next monday 9am'},
  {'role':'assistant','content':reply},
  {'role':'user','content':'yes, go ahead'}],
  'confirmed':True,'timezone':'$TZ_NAME'}))")
  code=$(call "$TOKEN" /api/ai/chat "$body")
  updates=$(jget "sum(1 for a in data.get('actions',[]) if a.startswith('update_task'))")
  echo "  update_task calls after 'yes': $updates"
  show_reply; show_usage
  if [[ "$code" == "200" && "${updates:-0}" -ge 1 ]]; then
    ok "confirmed turn executed remaining updates (verify Monday 9am in UI)"
  else bad "expected updates on confirmed turn (http=$code)"; fi
fi

# ---------------------------------------------------------------------------
say "TEST 7 · Metering — free plan gets 402, zero model calls"
if [[ -z "$FREE_TOKEN" ]]; then
  skip "no FREE_JWT supplied"
else
  code=$(call "$FREE_TOKEN" /api/ai/chat \
    "{\"messages\":[{\"role\":\"user\",\"content\":\"hello\"}],\"timezone\":\"$TZ_NAME\"}")
  errcode=$(jget "data.get('code','')")
  echo "  http=$code code=$errcode"
  if [[ "$code" == "402" && "$errcode" == "AI_LIMIT_REACHED" ]]; then
    ok "gate blocked before any provider spend"
  else bad "expected 402 AI_LIMIT_REACHED"; fi
fi

# ---------------------------------------------------------------------------
echo -e "\n${B}================ SUMMARY ================${N}"
echo -e "  ${G}PASS $PASS${N}   ${R}FAIL $FAIL${N}   ${Y}SKIP $SKIP${N}"
echo "  (Digest test is separate: DIGEST_ENABLED=true + fast DIGEST_CRON in .env,"
echo "   recreate backend, watch: docker logs -f taskmanager-backend | grep Digest)"
[[ $FAIL -eq 0 ]] && return 0 || return 1
}

# ---- run everything: live to terminal, clean copy to the output file ----
RAW="$(mktemp)"
run_all 2>&1 | tee "$RAW"
rc=${PIPESTATUS[0]}
sed -e 's/\x1b\[[0-9;]*m//g' "$RAW" > "$OUTFILE"
rm -f "$RAW"
echo ""
echo "Results saved to: $OUTFILE"
exit $rc
