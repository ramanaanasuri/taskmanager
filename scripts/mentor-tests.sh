#!/usr/bin/env bash
# ============================================================================
# mentor-tests.sh — end-to-end tests for the Learning InsightHub (mentoring) loop
#
# Usage:
#   ./mentor-tests.sh <MENTOR_JWT> <MEMBER_JWT> [OUTPUT_FILE]
#
#   MENTOR_JWT : token for the mentor account (the InsightHub owner) — required
#   MEMBER_JWT : token for a family-member account already registered — required
#   OUTPUT_FILE: where to save the report — optional; defaults to
#                mentor-test-results-<timestamp>.txt
#
#   Get a token: log in in the browser -> DevTools Console ->
#   localStorage.getItem('jwt_token')
#
#   The MEMBER account's email must exist as a Task Manager user (they must have
#   signed in once). Pass it so the mentor can add them:
#     MEMBER_EMAIL=family@example.com ./mentor-tests.sh <MENTOR_JWT> <MEMBER_JWT>
#
# Override the API base if needed:
#   API_BASE=https://api-taskmanager.sriinfosoft.com ./mentor-tests.sh ...
#
# Requires: curl, python3 (both present on the GCP/AWS instances)
# ============================================================================
set -u

API_BASE="${API_BASE:-https://api-taskmanager.gcp.sriinfosoft.com}"
MENTOR="${1:-}"
MEMBER="${2:-}"
MEMBER_EMAIL="${MEMBER_EMAIL:-}"
OUTFILE="${3:-mentor-test-results-$(date +%Y%m%d-%H%M%S).txt}"

if [[ -z "$MENTOR" || -z "$MEMBER" ]]; then
  echo "Usage: $0 <MENTOR_JWT> <MEMBER_JWT> [OUTPUT_FILE]"; exit 2
fi

PASS=0; FAIL=0; SKIP=0
G='\033[0;32m'; R='\033[0;31m'; Y='\033[0;33m'; B='\033[1m'; N='\033[0m'
say()  { echo -e "\n${B}== $1 ==${N}"; }
ok()   { echo -e "${G}PASS${N}  $1"; PASS=$((PASS+1)); }
bad()  { echo -e "${R}FAIL${N}  $1"; FAIL=$((FAIL+1)); }
skip() { echo -e "${Y}SKIP${N}  $1"; SKIP=$((SKIP+1)); }

# post <token> <path> <json>  -> writes /tmp/m_resp.json, echoes HTTP code
post() {
  curl -s -o /tmp/m_resp.json -w "%{http_code}" -X POST "$API_BASE$2" \
    -H "Authorization: Bearer $1" -H "Content-Type: application/json" -d "$3"
}
# get <token> <path> -> writes /tmp/m_resp.json, echoes HTTP code
get() {
  curl -s -o /tmp/m_resp.json -w "%{http_code}" -X GET "$API_BASE$2" \
    -H "Authorization: Bearer $1"
}
jget() {
  python3 -c "
import json
try:
    data=json.load(open('/tmp/m_resp.json'))
    print($1)
except Exception:
    print('')" 2>/dev/null
}

run_all() {
echo -e "${B}Learning InsightHub test run — $(date '+%Y-%m-%d %H:%M:%S') — $API_BASE${N}"

# --------------------------------------------------------------- 1 create
say "TEST 1 · mentor creates a InsightHub"
code=$(post "$MENTOR" /api/insight-hubs '{"name":"Family Investing"}')
CID=$(jget "data['id']")
role=$(jget "data['role']")
echo "  insightHubId=$CID role=$role http=$code"
if [[ "$code" == "201" && -n "$CID" && "$role" == "MENTOR" ]]; then
  ok "InsightHub created, caller is MENTOR"
else bad "expected 201 + MENTOR + id"; fi

# ------------------------------------------------------------ 2 add member
say "TEST 2 · mentor adds a member by email"
if [[ -z "$MEMBER_EMAIL" ]]; then
  skip "MEMBER_EMAIL not set — cannot test add-member (export MEMBER_EMAIL=...)"
else
  code=$(post "$MENTOR" "/api/insight-hubs/$CID/members" "{\"email\":\"$MEMBER_EMAIL\"}")
  st=$(jget "data.get('status','')")
  echo "  status=$st http=$code"
  if [[ "$code" == "201" || "$st" == "already a member" ]]; then
    ok "member added (or already present)"
  else bad "expected 201/added"; fi
fi

# --------------------------------------------- 3 unknown-email rejection
say "TEST 3 · adding a non-existent user is rejected"
code=$(post "$MENTOR" "/api/insight-hubs/$CID/members" '{"email":"nobody-xyz@nowhere.invalid"}')
echo "  http=$code"
[[ "$code" == "404" ]] && ok "unknown email -> 404" || bad "expected 404"

# ----------------------------------------------- 4 grounded happy path
say "TEST 4 · member asks a KB-answerable question -> DRAFTED with sources"
code=$(post "$MEMBER" "/api/insight-hubs/$CID/questions" \
  '{"text":"What is a Roth IRA and how are withdrawals taxed in retirement?"}')
QID=$(jget "data['id']")
status=$(jget "data['status']")
echo "  questionId=$QID status=$status http=$code"
echo "  (member view hides draft until delivered — draft is mentor-only)"
if [[ "$code" == "201" && -n "$QID" ]]; then
  if [[ "$status" == "DRAFTED" ]]; then ok "grounded -> DRAFTED"
  elif [[ "$status" == "NEEDS_MENTOR" ]]; then
    skip "escalated (KB may be empty/unreachable — set KB_INVEST_URLS, or mentor out of AI credit)"
  else bad "unexpected status $status"; fi
else bad "expected 201 + id"; fi

# ---------------------------------- 5 mentor sees draft + sources
say "TEST 5 · mentor sees the draft and its cited sources"
code=$(get "$MENTOR" "/api/questions/$QID")
draftlen=$(jget "len(str(data.get('draftText') or ''))")
nsrc=$(jget "len(data.get('sources') or [])")
echo "  draftLen=$draftlen sources=$nsrc http=$code"
if [[ "$code" == "200" && "$status" == "DRAFTED" ]]; then
  if [[ "$draftlen" -gt 0 && "$nsrc" -gt 0 ]]; then ok "draft present with >=1 source (citation invariant)"
  else bad "DRAFTED question must carry a non-empty draft and sources"; fi
else skip "no draft to inspect (question escalated in TEST 4)"; fi

# ---------------------------------- 6 member cannot see draft pre-delivery
say "TEST 6 · member cannot see the draft before approval"
code=$(get "$MEMBER" "/api/questions/$QID")
mdraft=$(jget "data.get('draftText','<absent>')")
echo "  member draftText=$mdraft http=$code"
if [[ "$code" == "200" && ( "$mdraft" == "<absent>" || -z "$mdraft" ) ]]; then
  ok "draft hidden from member until delivered"
else bad "member should NOT see draftText"; fi

# ---------------------------------- 7 approval gate -> delivery
say "TEST 7 · mentor approves -> DELIVERED, member now sees the answer"
if [[ "$status" == "DRAFTED" ]]; then
  code=$(post "$MENTOR" "/api/questions/$QID/approve" '{}')
  st=$(jget "data['status']")
  echo "  status=$st http=$code"
  [[ "$code" == "200" && "$st" == "DELIVERED" ]] && ok "approved -> DELIVERED" || bad "expected DELIVERED"
  # member now sees finalText
  get "$MEMBER" "/api/questions/$QID" >/dev/null
  flen=$(jget "len(str(data.get('finalText') or ''))")
  echo "  member finalText length=$flen"
  [[ "$flen" -gt 0 ]] && ok "member sees final answer after delivery" || bad "member should see finalText now"
else
  skip "TEST 4 escalated; approval path exercised via TEST 9 instead"
fi

# ---------------------------------- 8 escalation path (off-KB question)
say "TEST 8 · off-KB question escalates (never fabricates)"
code=$(post "$MEMBER" "/api/insight-hubs/$CID/questions" \
  '{"text":"Should I buy a specific rental condo in downtown Austin next month?"}')
QID2=$(jget "data['id']")
status2=$(jget "data['status']")
echo "  questionId=$QID2 status=$status2 http=$code"
[[ "$code" == "201" && "$status2" == "NEEDS_MENTOR" ]] && ok "off-KB -> NEEDS_MENTOR (escalated)" \
  || bad "expected NEEDS_MENTOR (got $status2)"

# ---------------------------------- 9 mentor answers escalated question
say "TEST 9 · mentor writes the answer for an escalated question -> DELIVERED"
if [[ -n "$QID2" ]]; then
  code=$(post "$MENTOR" "/api/questions/$QID2/answer" \
    '{"text":"That is a personal decision — here is how I would think through the tradeoffs..."}')
  st=$(jget "data['status']")
  echo "  status=$st http=$code"
  [[ "$code" == "200" && "$st" == "DELIVERED" ]] && ok "mentor answer -> DELIVERED" || bad "expected DELIVERED"
else skip "no escalated question id"; fi

# ---------------------------------- 10 ownership: outsider blocked
say "TEST 10 · a member cannot approve (mentor-only action)"
if [[ -n "$QID" ]]; then
  code=$(post "$MEMBER" "/api/questions/$QID/approve" '{}')
  echo "  http=$code"
  [[ "$code" == "403" ]] && ok "member approve -> 403" || bad "expected 403"
else skip "no question id"; fi

echo -e "\n${B}== SUMMARY ==${N}  ${G}PASS=$PASS${N}  ${R}FAIL=$FAIL${N}  ${Y}SKIP=$SKIP${N}"
}

# Print to terminal (color) and save to file (color stripped)
run_all 2>&1 | tee >(sed 's/\x1b\[[0-9;]*m//g' > "$OUTFILE")
echo "Report saved: $OUTFILE"
[[ "$FAIL" -eq 0 ]] || exit 1
