#!/usr/bin/env bash
# ═══════════════════════════════════════════════════════════════════════════
# E2E Acceptance Test — MVP Data Capture Foundation
# §3 Functional acceptance test from /home/mattew/SKD/data-capture-spec.md
#
# Usage:
#   ./scripts/e2e_data_capture_test.sh
#
# Environment variables:
#   GATEWAY        API gateway base URL   (default: http://localhost:28080)
#   PSQL           PostgreSQL exec prefix (default: kubectl exec ...)
#   TOKEN          Pre-set Bearer token   (skip registration/login if set)
#   TEST_EMAIL     Test user email        (default: e2e-<epoch>@test.local)
#   TEST_PASSWORD  Test user password     (default: E2eTestPass1)
#   AUTH_DEV_MODE  Auto-verify email via SQL before login (default: true)
#
# Corrections vs spec §3:
#   - items[2].id  (not .content_id) — ContentBatchItem field name is "id"
#   - ui.action_type  (not ui.event_type) — actual DB column is action_type
#   - action_type: canonical 6 (post-P1 hardening): IMPRESSION, OPEN, CLOSE,
#     LIKE, DISLIKE, BOOKMARK. Legacy names (VIEW/CLICK/SCROLL_PAST/SAVE/HIDE/
#     SHARE) still accepted by backend and mapped to canonical at persistence.
# ═══════════════════════════════════════════════════════════════════════════
set -euo pipefail

GATEWAY="${GATEWAY:-http://localhost:28080}"
PSQL="${PSQL:-kubectl exec -n skd postgres-0 -- psql -U postgres -d content_agg_db -t -A}"
TEST_EMAIL="${TEST_EMAIL:-e2e-$(date +%s)@test.local}"
TEST_PASSWORD="${TEST_PASSWORD:-E2eTestPass1}"
AUTH_DEV_MODE="${AUTH_DEV_MODE:-true}"
TOKEN="${TOKEN:-}"

# ── port-forward management + cleanup trap ──────────────────────────────────
PF_PID=""
trap '[[ -n "$PF_PID" ]] && kill "$PF_PID" 2>/dev/null; $PSQL -c "DELETE FROM auth.users WHERE email LIKE '\''e2e-%@test.local'\'';" >/dev/null 2>&1; exit' EXIT INT TERM

if ! curl -sf "${GATEWAY}/health" >/dev/null 2>&1; then
    echo "→ Starting kubectl port-forward → ${GATEWAY}"
    kubectl port-forward -n skd svc/api-gateway "${GATEWAY##*:}:8080" >/dev/null 2>&1 &
    PF_PID=$!
    for i in {1..10}; do sleep 1; curl -sf "${GATEWAY}/health" >/dev/null 2>&1 && break; done
fi

PASS=0; FAIL=0; TOTAL=0

# ── colour helpers ──────────────────────────────────────────────────────────
if command -v tput &>/dev/null && [ -t 1 ]; then
  GRN=$(tput setaf 2); RED=$(tput setaf 1); YLW=$(tput setaf 3)
  BLD=$(tput bold);    RST=$(tput sgr0)
else
  GRN=''; RED=''; YLW=''; BLD=''; RST=''
fi

step()  { echo; echo "${BLD}──────────────────────────────────────────────${RST}"; echo "${BLD}STEP $*${RST}"; }
pass()  { echo "  ${GRN}✓${RST} $*"; PASS=$((PASS+1)); TOTAL=$((TOTAL+1)); }
fail()  { echo "  ${RED}✗${RST} $*"; FAIL=$((FAIL+1)); TOTAL=$((TOTAL+1)); }
info()  { echo "    ${YLW}→${RST} $*"; }
abort() { echo; echo "${RED}${BLD}ABORT: $*${RST}"; exit 1; }

# ── uuid helper ─────────────────────────────────────────────────────────────
gen_uuid() {
  if command -v uuidgen &>/dev/null; then
    uuidgen | tr '[:upper:]' '[:lower:]'
  else
    python3 -c "import uuid; print(uuid.uuid4())"
  fi
}

# ── psql helper: run a query and return stdout ───────────────────────────────
run_sql() {
  $PSQL -c "$1"
}

# ══════════════════════════════════════════════════════════════════════════════
# STEP 1 — Authenticate
# ══════════════════════════════════════════════════════════════════════════════
step "1 — Authenticate"

if [ -n "$TOKEN" ]; then
  info "Using pre-set \$TOKEN — skipping registration/login."
else
  info "Registering test user: ${TEST_EMAIL}"
  REG_RESP=$(curl -s -o /dev/null -w "%{http_code}" \
    -X POST "${GATEWAY}/api/auth/register" \
    -H "Content-Type: application/json" \
    -d "{\"email\":\"${TEST_EMAIL}\",\"password\":\"${TEST_PASSWORD}\"}")

  if [ "$REG_RESP" = "201" ] || [ "$REG_RESP" = "409" ]; then
    pass "Registration returned HTTP ${REG_RESP} (201=new, 409=already exists)"
  else
    fail "Registration failed: HTTP ${REG_RESP}"
    abort "Cannot continue without a valid user. Check auth-service logs."
  fi

  if [[ "${AUTH_DEV_MODE}" == "true" ]]; then
    $PSQL -c "UPDATE auth.users SET email_verified=true WHERE email='${TEST_EMAIL}';" >/dev/null
    echo "  ✓ Email auto-verified (dev-mode)"
  fi

  info "Logging in..."

  LOGIN_BODY=$(curl -s \
    -X POST "${GATEWAY}/api/auth/login" \
    -H "Content-Type: application/json" \
    -d "{\"email\":\"${TEST_EMAIL}\",\"password\":\"${TEST_PASSWORD}\"}")

  TOKEN=$(echo "$LOGIN_BODY" | python3 -c "import sys,json; d=json.load(sys.stdin); print(d.get('access_token',''))" 2>/dev/null || true)

  if [ -z "$TOKEN" ]; then
    fail "Login failed — no access_token in response"
    info "Response: ${LOGIN_BODY}"
    abort "Cannot continue without a Bearer token. Set AUTH_DEV_MODE=true or set \$TOKEN."
  fi
  pass "Login successful — token acquired"
fi

# ══════════════════════════════════════════════════════════════════════════════
# STEP 2 — Onboard (best-effort; skip if already onboarded)
# ══════════════════════════════════════════════════════════════════════════════
step "2 — Onboard user (if not already)"

ONBOARD_RESP=$(curl -s -o /dev/null -w "%{http_code}" \
  -X POST "${GATEWAY}/api/users/me/onboarding" \
  -H "Authorization: Bearer ${TOKEN}" \
  -H "Content-Type: application/json" \
  -d '{"categories":["технологии","наука","бизнес"],"source_content_ids":[]}')

if [ "$ONBOARD_RESP" = "200" ]; then
  pass "Onboarding completed (HTTP 200)"
elif [ "$ONBOARD_RESP" = "409" ]; then
  pass "Already onboarded (HTTP 409 — expected for returning test user)"
else
  fail "Unexpected onboarding response: HTTP ${ONBOARD_RESP}"
fi

# ══════════════════════════════════════════════════════════════════════════════
# STEP 3 — GET /api/feed — capture X-Request-Id + item at position 3
# ══════════════════════════════════════════════════════════════════════════════
step "3 — GET /api/feed — capture X-Request-Id and item at position 3 (1-indexed)"

FEED_RESPONSE=$(curl -si \
  "${GATEWAY}/api/feed" \
  -H "Authorization: Bearer ${TOKEN}")

HTTP_STATUS=$(awk 'NR==1 { match($0,/[0-9][0-9][0-9]/); print substr($0,RSTART,3); exit }' <<< "$FEED_RESPONSE")
if [ "$HTTP_STATUS" = "200" ]; then
  pass "GET /api/feed returned HTTP 200"
else
  fail "GET /api/feed returned HTTP ${HTTP_STATUS} (expected 200)"
  abort "Cannot continue — feed endpoint failed."
fi

# Extract X-Request-Id from response headers (case-insensitive)
REQUEST_ID=$(awk 'tolower($1)=="x-request-id:" { gsub(/\r/,"",$2); print $2; exit }' <<< "$FEED_RESPONSE")
if [ -n "$REQUEST_ID" ]; then
  pass "X-Request-Id header present: ${REQUEST_ID}"
else
  fail "X-Request-Id header missing from GET /api/feed response"
  info "Headers received:"
  echo "$FEED_RESPONSE" | head -20
  abort "X-Request-Id required for JOIN query. Check api-gateway RequestIdFilter."
fi

# Extract X-Feed-Source header
FEED_SOURCE=$(awk 'tolower($1)=="x-feed-source:" { gsub(/\r/,"",$2); print $2; exit }' <<< "$FEED_RESPONSE")
if [ -n "$FEED_SOURCE" ]; then
  pass "X-Feed-Source header present: ${FEED_SOURCE}"
else
  fail "X-Feed-Source header missing from GET /api/feed response"
fi

# Extract JSON body (everything after the blank line)
FEED_BODY=$(awk 'found { print; next } /^\r?$/ { found=1 }' <<< "$FEED_RESPONSE")

# Get the item at 0-based index 2 (= position 3, 1-indexed)
# ContentBatchItem has an "id" field (not "content_id")
ITEM_COUNT=$(echo "$FEED_BODY" | python3 -c "import sys,json; d=json.load(sys.stdin); print(len(d.get('items',[])))" 2>/dev/null || echo "0")
info "Feed returned ${ITEM_COUNT} items"

if [ "${ITEM_COUNT:-0}" -lt 3 ]; then
  fail "Feed returned fewer than 3 items (${ITEM_COUNT}) — cannot test position 3"
  abort "Need at least 3 feed items. Ensure content exists in published_content table."
fi
pass "Feed has ≥3 items"

ITEM_AT_POSITION_3=$(echo "$FEED_BODY" | python3 -c "
import sys, json
d = json.load(sys.stdin)
items = d.get('items', [])
# ContentBatchItem uses field 'id' (not 'content_id')
item = items[2]
print(item.get('id', item.get('content_id', '')))
" 2>/dev/null || true)

if [ -n "$ITEM_AT_POSITION_3" ]; then
  pass "Item at position 3 (0-indexed 2): content_id = ${ITEM_AT_POSITION_3}"
else
  fail "Could not extract content_id from items[2]"
  info "Feed body (first 300 chars): ${FEED_BODY:0:300}"
  abort "Cannot proceed without a content_id for the interaction event."
fi

# ══════════════════════════════════════════════════════════════════════════════
# STEP 4 — Verify feed.feed_requests row was written
# ══════════════════════════════════════════════════════════════════════════════
step "4 — Verify feed.feed_requests row for request_id = ${REQUEST_ID}"

FR_ROW=$(run_sql "SELECT source, count_returned FROM feed.feed_requests WHERE request_id='${REQUEST_ID}'" 2>/dev/null || echo "")

if [ -n "$FR_ROW" ]; then
  pass "feed.feed_requests row exists: ${FR_ROW}"
  FEED_SOURCE_DB=$(echo "$FR_ROW" | cut -d'|' -f1 | tr -d ' ')
  COUNT_RETURNED=$(echo "$FR_ROW" | cut -d'|' -f2 | tr -d ' ')
  info "  source='${FEED_SOURCE_DB}'  count_returned=${COUNT_RETURNED}"
  if [ "${COUNT_RETURNED:-0}" -gt 0 ]; then
    pass "count_returned = ${COUNT_RETURNED} (> 0)"
  else
    fail "count_returned = 0 — feed_requests row has no items recorded"
  fi
else
  fail "No feed.feed_requests row found for request_id='${REQUEST_ID}'"
  info "FeedLogService async write may be slow — check feed-service logs."
fi

# ══════════════════════════════════════════════════════════════════════════════
# STEP 5 — Verify feed.feed_items rows exist with scoring_components populated
# ══════════════════════════════════════════════════════════════════════════════
step "5 — Verify feed.feed_items rows for request_id = ${REQUEST_ID}"

FI_COUNT=$(run_sql "SELECT COUNT(*) FROM feed.feed_items WHERE request_id='${REQUEST_ID}'" 2>/dev/null | tr -d ' ' || echo "0")
if [ "${FI_COUNT:-0}" -gt 0 ]; then
  pass "feed.feed_items has ${FI_COUNT} rows for this request"
else
  fail "No feed.feed_items rows found for request_id='${REQUEST_ID}'"
  info "Check FeedLogService async executor and FeedController wiring."
fi

FI_WITH_SCORING=$(run_sql "SELECT COUNT(*) FROM feed.feed_items WHERE request_id='${REQUEST_ID}' AND scoring_components IS NOT NULL" 2>/dev/null | tr -d ' ' || echo "0")
if [ "${FI_WITH_SCORING:-0}" -gt 0 ]; then
  pass "feed.feed_items has ${FI_WITH_SCORING} rows with scoring_components populated"
else
  fail "scoring_components is NULL for all feed_items — breakdown from rec-system not captured"
  info "Check FeedService.getRecommendationsWithBreakdown() and RecSystemClient."
fi

# ══════════════════════════════════════════════════════════════════════════════
# STEP 6 — POST /api/interactions/batch — LIKE on item at position 3
# ══════════════════════════════════════════════════════════════════════════════
step "6 — POST /api/interactions/batch (LIKE at position 3)"

EVENT_ID=$(gen_uuid)
EVENT_TS=$(date -u +%Y-%m-%dT%H:%M:%SZ)

# NOTE: action_type="LIKE" — canonical 6 set (post-P1): IMPRESSION, OPEN, CLOSE, LIKE, DISLIKE, BOOKMARK.
# position_in_feed is 1-indexed per API_CONTRACTS.md
# scroll_depth + metadata are persisted after P2 migration (20260420__add_scroll_depth_metadata.sql).

INTERACTION_BODY=$(cat <<EOF
{
  "events": [{
    "content_id": "${ITEM_AT_POSITION_3}",
    "action_type": "LIKE",
    "duration_sec": null,
    "timestamp": "${EVENT_TS}",
    "schema_version": 2,
    "feed_request_id": "${REQUEST_ID}",
    "position_in_feed": 3,
    "device_type": "e2e-test",
    "app_version": "e2e-1.0.0",
    "ab_bucket": 0,
    "scroll_depth": 0.85,
    "metadata": {"source": "e2e"}
  }]
}
EOF
)

INT_STATUS=$(curl -s -o /tmp/e2e_interaction_resp.json -w "%{http_code}" \
  -X POST "${GATEWAY}/api/interactions/batch" \
  -H "Authorization: Bearer ${TOKEN}" \
  -H "Content-Type: application/json" \
  -d "${INTERACTION_BODY}")

INT_BODY=$(cat /tmp/e2e_interaction_resp.json)

if [ "$INT_STATUS" = "202" ]; then
  ACCEPTED=$(echo "$INT_BODY" | python3 -c "import sys,json; d=json.load(sys.stdin); print(d.get('accepted',0))" 2>/dev/null || echo "?")
  REJECTED=$(echo "$INT_BODY" | python3 -c "import sys,json; d=json.load(sys.stdin); print(d.get('rejected',0))" 2>/dev/null || echo "?")
  pass "POST /api/interactions/batch → HTTP 202 (accepted=${ACCEPTED}, rejected=${REJECTED})"
  if [ "$ACCEPTED" != "1" ] || [ "$REJECTED" != "0" ]; then
    fail "Expected accepted=1 rejected=0, got accepted=${ACCEPTED} rejected=${REJECTED}"
    info "Response: ${INT_BODY}"
  fi
else
  fail "POST /api/interactions/batch → HTTP ${INT_STATUS} (expected 202)"
  info "Response: ${INT_BODY}"
  abort "Interaction not accepted — cannot validate JOIN query."
fi

# ══════════════════════════════════════════════════════════════════════════════
# STEP 7 — Wait for Kafka consumer to process the interaction
# ══════════════════════════════════════════════════════════════════════════════
step "7 — Waiting 5 seconds for Kafka consumer + DB flush"
info "user-interactions-service: DB write is synchronous (before Kafka publish)."
info "rec-system Kafka consumer: async — 5s is usually sufficient."
sleep 5
pass "Wait complete"

# ══════════════════════════════════════════════════════════════════════════════
# STEP 8 — Run the money JOIN query
# ══════════════════════════════════════════════════════════════════════════════
step "8 — The money JOIN query (§3 acceptance test)"

# NOTE: spec §3 uses ui.event_type — actual DB column is ui.action_type
# NOTE: post-P1 canonical — we send 'LIKE', DB stores 'LIKE' (canonical) directly.

JOIN_QUERY="SELECT fr.user_id, fi.position, fi.content_id::text,
       CASE WHEN fi.scoring_components IS NOT NULL THEN 'populated' ELSE 'null' END AS scoring,
       COALESCE(ui.action_type, 'NULL') AS action_type
FROM feed.feed_requests fr
JOIN feed.feed_items fi
     ON fi.request_id = fr.request_id
LEFT JOIN interactions.user_interactions ui
     ON ui.feed_request_id = fr.request_id
     AND ui.content_id = fi.content_id
WHERE fr.request_id = '${REQUEST_ID}'
ORDER BY fi.position"

info "Running JOIN query..."
JOIN_RESULT=$(run_sql "$JOIN_QUERY" 2>/dev/null || echo "QUERY_FAILED")

if echo "$JOIN_RESULT" | grep -q "QUERY_FAILED"; then
  fail "JOIN query execution failed — check PostgreSQL connectivity and schema"
  abort "Cannot validate acceptance criteria."
fi

ROW_COUNT=$(echo "$JOIN_RESULT" | grep -c '[|]' || echo "0")
if [ "${ROW_COUNT:-0}" -gt 0 ]; then
  pass "JOIN query returned ${ROW_COUNT} rows"
else
  fail "JOIN query returned 0 rows — tables may not have been populated"
fi

echo
info "JOIN result:"
echo "$JOIN_RESULT" | head -20

# Verify: exactly one row has action_type='LIKE' at position=3 + scroll_depth + metadata persisted (P2)
LIKE_AT_POS3=$(run_sql "SELECT COUNT(*) FROM feed.feed_requests fr
JOIN feed.feed_items fi ON fi.request_id = fr.request_id
LEFT JOIN interactions.user_interactions ui
     ON ui.feed_request_id = fr.request_id
     AND ui.content_id = fi.content_id
WHERE fr.request_id = '${REQUEST_ID}'
  AND fi.position = 3
  AND ui.action_type = 'LIKE'
  AND abs(ui.scroll_depth - 0.85) < 0.001
  AND ui.metadata->>'source' = 'e2e'" 2>/dev/null | tr -d ' ' || echo "0")

if [ "${LIKE_AT_POS3:-0}" = "1" ]; then
  pass "Money JOIN confirms: LIKE at position 3 + scroll_depth=0.85 + metadata.source=e2e — training pair captured ✓"
else
  fail "Expected 1 row with action_type='LIKE', scroll_depth=0.85, metadata.source='e2e' at position=3, found: ${LIKE_AT_POS3}"
  info "The interaction may not have been persisted with canonical ActionType + P2 columns."
  info "Check: P1 ActionType.kt (canonical 6), P2 migration (scroll_depth+metadata), InteractionProcessor."
fi

# Verify: other rows have NULL action_type (no spurious matches)
NULL_ROWS=$(run_sql "SELECT COUNT(*) FROM feed.feed_requests fr
JOIN feed.feed_items fi ON fi.request_id = fr.request_id
LEFT JOIN interactions.user_interactions ui
     ON ui.feed_request_id = fr.request_id
     AND ui.content_id = fi.content_id
WHERE fr.request_id = '${REQUEST_ID}'
  AND ui.action_type IS NULL" 2>/dev/null | tr -d ' ' || echo "0")

if [ "${NULL_ROWS:-0}" -ge 1 ]; then
  pass "Other ${NULL_ROWS} feed_items rows have no matched interaction (NULL action_type — expected)"
else
  info "Note: all rows matched an interaction (unexpected, but not necessarily wrong)"
fi

# ══════════════════════════════════════════════════════════════════════════════
# STEP 9 — Additional spot checks
# ══════════════════════════════════════════════════════════════════════════════
step "9 — Additional health checks"

# rec-system /metrics
METRICS_STATUS=$(curl -s -o /dev/null -w "%{http_code}" "http://${REC_SYSTEM_HOST:-localhost}:8000/metrics" 2>/dev/null || echo "000")
if [ "$METRICS_STATUS" = "200" ]; then
  pass "rec-system GET /metrics → HTTP 200"
else
  info "rec-system GET /metrics → HTTP ${METRICS_STATUS} (direct: use REC_SYSTEM_HOST env var if needed)"
fi

# feed-service actuator health
ACTUATOR_STATUS=$(curl -s -o /dev/null -w "%{http_code}" "${GATEWAY}/actuator/health" 2>/dev/null || echo "000")
if [ "$ACTUATOR_STATUS" = "200" ]; then
  pass "feed-service GET /actuator/health → HTTP 200 (via gateway)"
else
  info "GET /actuator/health → HTTP ${ACTUATOR_STATUS} (may require direct access to feed-service:8085)"
fi

# ══════════════════════════════════════════════════════════════════════════════
# SUMMARY
# ══════════════════════════════════════════════════════════════════════════════
echo
echo "${BLD}══════════════════════════════════════════════════════════════${RST}"
echo "${BLD}MVP Data Capture Foundation — E2E Acceptance Test SUMMARY${RST}"
echo "══════════════════════════════════════════════════════════════"
echo "  ${GRN}PASS${RST}: ${PASS} / ${TOTAL}"
if [ $FAIL -gt 0 ]; then
  echo "  ${RED}FAIL${RST}: ${FAIL} / ${TOTAL}"
else
  echo "  FAIL: 0 / ${TOTAL}"
fi
echo "══════════════════════════════════════════════════════════════"

if [ $FAIL -eq 0 ]; then
  echo
  echo "${GRN}${BLD}  ✓  ALL CHECKS PASSED${RST}"
  echo "${GRN}  The JOIN query links: feed_request → scored feed_items → interaction event.${RST}"
  echo "${GRN}  MVP Data Capture Foundation is validated end-to-end.${RST}"
  echo
  exit 0
else
  echo
  echo "${RED}${BLD}  ✗  ${FAIL} CHECK(S) FAILED — review output above for details.${RST}"
  echo
  exit 1
fi
