#!/bin/bash
# E2E Integration Test Script for SKD Platform
# Tests the full pipeline: sources → parsing → dedup → rec → user → feed

set -euo pipefail

GATEWAY_URL="http://localhost:8080"
CONFIG_SERVICE_URL="http://localhost:18081"
PARSER_SERVICE_URL="http://localhost:18082"
AGGREGATOR_URL="http://localhost:8086"
REC_SYSTEM_URL="http://localhost:8000"
HMAC_SECRET="super-secret-hmac-key-for-dev"

RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m'

ISSUES=()
PASS=0
FAIL=0
TOTAL=0

log_pass() { ((PASS++)); ((TOTAL++)); echo -e "${GREEN}[PASS]${NC} $1"; }
log_fail() { ((FAIL++)); ((TOTAL++)); echo -e "${RED}[FAIL]${NC} $1"; ISSUES+=("$1: $2"); }
log_info() { echo -e "${YELLOW}[INFO]${NC} $1"; }

# ============================================================
# 1. Health Checks
# ============================================================
log_info "=== Phase 1: Health Checks ==="

check_health() {
    local name=$1 url=$2
    local resp
    resp=$(curl -sf "$url" 2>/dev/null) && log_pass "$name health OK" || log_fail "$name health" "unreachable at $url"
}

check_health "API Gateway" "$GATEWAY_URL/health"
check_health "Config Service" "$CONFIG_SERVICE_URL/actuator/health"
check_health "Parser Service" "$PARSER_SERVICE_URL/actuator/health"
check_health "Aggregator Service" "$AGGREGATOR_URL/actuator/health"
check_health "Rec System" "$REC_SYSTEM_URL/health"

# ============================================================
# 2. Add Content Sources (Habr, VC.RU)
# ============================================================
log_info "=== Phase 2: Add Content Sources ==="

# Add Habr source (company)
HABR_RESP=$(curl -sf -X POST "$CONFIG_SERVICE_URL/api/v1/sources/habr" \
    -H "Content-Type: application/json" \
    -d '{
        "name": "Habr - Yandex",
        "companyAlias": "yandex",
        "parseComments": false,
        "parseImages": true,
        "maxArticles": 5,
        "updateFrequencyMinutes": 60
    }' 2>/dev/null) && {
    HABR_SOURCE_ID=$(echo "$HABR_RESP" | python3 -c "import sys,json; print(json.load(sys.stdin).get('id',''))" 2>/dev/null || echo "")
    if [ -n "$HABR_SOURCE_ID" ]; then
        log_pass "Habr source created: $HABR_SOURCE_ID"
    else
        log_fail "Habr source creation" "No ID in response: $HABR_RESP"
    fi
} || log_fail "Habr source creation" "HTTP error"

# Add VC.RU source
VCRU_RESP=$(curl -sf -X POST "$CONFIG_SERVICE_URL/api/v1/sources/vcru" \
    -H "Content-Type: application/json" \
    -d '{
        "name": "VC.RU - TechCrunch",
        "alias": "techcrunch",
        "parseImages": true,
        "maxArticles": 5,
        "sorting": "new",
        "updateFrequencyMinutes": 60
    }' 2>/dev/null) && {
    VCRU_SOURCE_ID=$(echo "$VCRU_RESP" | python3 -c "import sys,json; print(json.load(sys.stdin).get('id',''))" 2>/dev/null || echo "")
    if [ -n "$VCRU_SOURCE_ID" ]; then
        log_pass "VC.RU source created: $VCRU_SOURCE_ID"
    else
        log_fail "VC.RU source creation" "No ID in response: $VCRU_RESP"
    fi
} || log_fail "VC.RU source creation" "HTTP error"

# Add second Habr source (hub)
HABR2_RESP=$(curl -sf -X POST "$CONFIG_SERVICE_URL/api/v1/sources/habr" \
    -H "Content-Type: application/json" \
    -d '{
        "name": "Habr - Machine Learning Hub",
        "hubAlias": "machine_learning",
        "parseComments": false,
        "parseImages": true,
        "maxArticles": 5,
        "updateFrequencyMinutes": 60
    }' 2>/dev/null) && {
    HABR2_SOURCE_ID=$(echo "$HABR2_RESP" | python3 -c "import sys,json; print(json.load(sys.stdin).get('id',''))" 2>/dev/null || echo "")
    if [ -n "$HABR2_SOURCE_ID" ]; then
        log_pass "Habr ML hub source created: $HABR2_SOURCE_ID"
    else
        log_fail "Habr ML hub source creation" "No ID in response"
    fi
} || log_fail "Habr ML hub source creation" "HTTP error"

# Verify sources list
SOURCES_COUNT=$(curl -sf "$CONFIG_SERVICE_URL/api/v1/sources" 2>/dev/null | python3 -c "import sys,json; data=json.load(sys.stdin); print(len(data) if isinstance(data, list) else data.get('totalElements', 0))" 2>/dev/null || echo "0")
if [ "$SOURCES_COUNT" -ge 3 ] 2>/dev/null; then
    log_pass "Sources list: $SOURCES_COUNT sources"
else
    log_fail "Sources verification" "Expected >=3, got $SOURCES_COUNT"
fi

# ============================================================
# 3. Trigger Parsing
# ============================================================
log_info "=== Phase 3: Trigger Content Parsing ==="

PARSE_RESP=$(curl -sf -X POST "$PARSER_SERVICE_URL/api/v1/parser/parse-all" 2>/dev/null)
if [ $? -eq 0 ]; then
    log_pass "Parse-all triggered"
else
    log_fail "Parse-all trigger" "HTTP error"
fi

# Wait for parsing to complete (poll raw_content count)
log_info "Waiting for parsing (up to 3 minutes)..."
for i in $(seq 1 36); do
    RAW_COUNT=$(docker exec skd-postgres psql -U postgres -d content_agg_db -t -c \
        "SELECT COUNT(*) FROM data_flow.raw_content WHERE processing_status='COMPLETED'" 2>/dev/null | tr -d ' ')
    if [ "${RAW_COUNT:-0}" -gt 0 ] 2>/dev/null; then
        log_pass "Parsing complete: $RAW_COUNT raw articles"
        break
    fi
    if [ $i -eq 36 ]; then
        log_fail "Parsing timeout" "No articles after 3 minutes"
    fi
    sleep 5
done

# ============================================================
# 4. Wait for Dedup + Rec Processing
# ============================================================
log_info "=== Phase 4: Wait for Dedup & Rec Processing ==="

log_info "Waiting for dedup processing (up to 5 minutes)..."
for i in $(seq 1 60); do
    DEDUP_COUNT=$(docker exec skd-postgres psql -U postgres -d content_agg_db -t -c \
        "SELECT COUNT(*) FROM data_flow.raw_content WHERE is_processed_by_dedup=true" 2>/dev/null | tr -d ' ')
    RAW_TOTAL=$(docker exec skd-postgres psql -U postgres -d content_agg_db -t -c \
        "SELECT COUNT(*) FROM data_flow.raw_content WHERE processing_status='COMPLETED'" 2>/dev/null | tr -d ' ')
    if [ "${DEDUP_COUNT:-0}" -gt 0 ] 2>/dev/null && [ "${DEDUP_COUNT:-0}" -ge "${RAW_TOTAL:-1}" ] 2>/dev/null; then
        log_pass "Dedup processing complete: $DEDUP_COUNT/$RAW_TOTAL"
        break
    fi
    if [ $i -eq 60 ]; then
        log_fail "Dedup timeout" "Processed $DEDUP_COUNT/$RAW_TOTAL after 5 minutes"
    fi
    sleep 5
done

log_info "Waiting for rec processing (up to 5 minutes)..."
for i in $(seq 1 60); do
    REC_COUNT=$(docker exec skd-postgres psql -U postgres -d content_agg_db -t -c \
        "SELECT COUNT(*) FROM data_flow.raw_content WHERE is_processed_by_rec=true" 2>/dev/null | tr -d ' ')
    if [ "${REC_COUNT:-0}" -gt 0 ] 2>/dev/null; then
        log_pass "Rec processing: $REC_COUNT articles"
        break
    fi
    if [ $i -eq 60 ]; then
        log_fail "Rec processing timeout" "0 processed after 5 minutes"
    fi
    sleep 5
done

# Wait for publishing
log_info "Waiting for published content..."
for i in $(seq 1 24); do
    PUB_COUNT=$(docker exec skd-postgres psql -U postgres -d content_agg_db -t -c \
        "SELECT COUNT(*) FROM data_flow.published_content" 2>/dev/null | tr -d ' ')
    if [ "${PUB_COUNT:-0}" -gt 0 ] 2>/dev/null; then
        log_pass "Published content: $PUB_COUNT articles"
        break
    fi
    if [ $i -eq 24 ]; then
        log_fail "Publishing timeout" "No published content after 2 minutes"
    fi
    sleep 5
done

# ============================================================
# 5. Test Aggregator API
# ============================================================
log_info "=== Phase 5: Aggregator API ==="

LATEST_RESP=$(curl -sf "$AGGREGATOR_URL/api/v1/content/latest?page=0&size=10" 2>/dev/null)
if [ -n "$LATEST_RESP" ]; then
    LATEST_COUNT=$(echo "$LATEST_RESP" | python3 -c "import sys,json; d=json.load(sys.stdin); print(d.get('total_elements', d.get('totalElements', 0)))" 2>/dev/null || echo "0")
    if [ "${LATEST_COUNT:-0}" -gt 0 ] 2>/dev/null; then
        log_pass "Aggregator /latest: $LATEST_COUNT articles"
    else
        log_fail "Aggregator /latest" "0 articles"
    fi
else
    log_fail "Aggregator /latest" "No response"
fi

# ============================================================
# 6. User Registration via Gateway
# ============================================================
log_info "=== Phase 6: User Registration ==="

REGISTER_RESP=$(curl -sf -X POST "$GATEWAY_URL/api/auth/register" \
    -H "Content-Type: application/json" \
    -d '{
        "email": "testuser@example.com",
        "password": "TestPassword123!"
    }' 2>/dev/null)
if [ -n "$REGISTER_RESP" ]; then
    USER_ID=$(echo "$REGISTER_RESP" | python3 -c "import sys,json; d=json.load(sys.stdin); print(d.get('user_id', d.get('userId', '')))" 2>/dev/null || echo "")
    if [ -n "$USER_ID" ]; then
        log_pass "User registered: $USER_ID"
    else
        log_fail "User registration" "No user_id in response: $REGISTER_RESP"
    fi
else
    log_fail "User registration" "No response"
fi

# Login (dev mode skips email verification)
LOGIN_RESP=$(curl -sf -X POST "$GATEWAY_URL/api/auth/login" \
    -H "Content-Type: application/json" \
    -d '{
        "email": "testuser@example.com",
        "password": "TestPassword123!"
    }' 2>/dev/null)
if [ -n "$LOGIN_RESP" ]; then
    ACCESS_TOKEN=$(echo "$LOGIN_RESP" | python3 -c "import sys,json; d=json.load(sys.stdin); print(d.get('access_token', d.get('accessToken', '')))" 2>/dev/null || echo "")
    REFRESH_TOKEN=$(echo "$LOGIN_RESP" | python3 -c "import sys,json; d=json.load(sys.stdin); print(d.get('refresh_token', d.get('refreshToken', '')))" 2>/dev/null || echo "")
    if [ -n "$ACCESS_TOKEN" ]; then
        log_pass "User login OK, got access token"
    else
        log_fail "User login" "No access_token: $LOGIN_RESP"
    fi
else
    log_fail "User login" "No response"
fi

# ============================================================
# 7. Rec System — Categories & Onboarding
# ============================================================
log_info "=== Phase 7: Rec System Categories & Onboarding ==="

# Get categories (direct call to rec-system)
CATEGORIES_RESP=$(curl -sf "$REC_SYSTEM_URL/categories" 2>/dev/null)
if [ -n "$CATEGORIES_RESP" ]; then
    CAT_COUNT=$(echo "$CATEGORIES_RESP" | python3 -c "import sys,json; d=json.load(sys.stdin); print(len(d.get('categories',[])))" 2>/dev/null || echo "0")
    if [ "${CAT_COUNT:-0}" -gt 0 ] 2>/dev/null; then
        log_pass "Categories: $CAT_COUNT available"
    else
        log_fail "Categories" "Empty list"
    fi
else
    log_fail "Categories" "No response"
fi

# Onboarding (direct call to rec-system, skip gateway for now)
if [ -n "${USER_ID:-}" ]; then
    ONBOARD_RESP=$(curl -sf -X POST "$REC_SYSTEM_URL/onboarding" \
        -H "Content-Type: application/json" \
        -d "{
            \"user_id\": \"$USER_ID\",
            \"categories\": [\"технологии\", \"наука\", \"бизнес\"]
        }" 2>/dev/null)
    if [ -n "$ONBOARD_RESP" ]; then
        PROFILE_INIT=$(echo "$ONBOARD_RESP" | python3 -c "import sys,json; print(json.load(sys.stdin).get('profile_initialized', False))" 2>/dev/null || echo "False")
        if [ "$PROFILE_INIT" = "True" ]; then
            log_pass "User onboarding complete"
        else
            log_fail "User onboarding" "profile_initialized=false: $ONBOARD_RESP"
        fi
    else
        log_fail "User onboarding" "No response"
    fi

    # ============================================================
    # 8. Get Feed / Recommendations
    # ============================================================
    log_info "=== Phase 8: Feed & Recommendations ==="

    # Direct rec-system call
    REC_RESP=$(curl -sf -X POST "$REC_SYSTEM_URL/recommendations" \
        -H "Content-Type: application/json" \
        -d "{\"user_id\": \"$USER_ID\", \"count\": 30}" 2>/dev/null)
    if [ -n "$REC_RESP" ]; then
        REC_COUNT=$(echo "$REC_RESP" | python3 -c "import sys,json; d=json.load(sys.stdin); print(len(d.get('items',[])))" 2>/dev/null || echo "0")
        if [ "${REC_COUNT:-0}" -gt 0 ] 2>/dev/null; then
            log_pass "Recommendations: $REC_COUNT items for user"
        else
            log_fail "Recommendations" "0 items: $REC_RESP"
        fi
    else
        log_fail "Recommendations" "No response"
    fi

    # Cold-start endpoint
    COLD_RESP=$(curl -sf "$REC_SYSTEM_URL/recommendations/cold-start?count=10" 2>/dev/null)
    if [ -n "$COLD_RESP" ]; then
        COLD_COUNT=$(echo "$COLD_RESP" | python3 -c "import sys,json; d=json.load(sys.stdin); print(len(d.get('items',[])))" 2>/dev/null || echo "0")
        log_pass "Cold-start: $COLD_COUNT items"
    else
        log_fail "Cold-start" "No response"
    fi
else
    log_info "Skipping onboarding/recommendations — no user_id"
fi

# ============================================================
# 9. Edge Cases
# ============================================================
log_info "=== Phase 9: Edge Cases ==="

# Non-existent user recommendations
NOUSER_RESP=$(curl -s -o /dev/null -w "%{http_code}" -X POST "$REC_SYSTEM_URL/recommendations" \
    -H "Content-Type: application/json" \
    -d '{"user_id": "00000000-0000-0000-0000-000000000000", "count": 10}' 2>/dev/null)
if [ "$NOUSER_RESP" = "404" ]; then
    log_pass "Non-existent user returns 404"
else
    log_fail "Non-existent user" "Expected 404, got $NOUSER_RESP"
fi

# Duplicate registration
DUP_REG_CODE=$(curl -s -o /dev/null -w "%{http_code}" -X POST "$GATEWAY_URL/api/auth/register" \
    -H "Content-Type: application/json" \
    -d '{"email": "testuser@example.com", "password": "TestPassword123!"}' 2>/dev/null)
if [ "$DUP_REG_CODE" = "409" ]; then
    log_pass "Duplicate registration returns 409"
else
    log_fail "Duplicate registration" "Expected 409, got $DUP_REG_CODE"
fi

# Invalid login
INVALID_LOGIN_CODE=$(curl -s -o /dev/null -w "%{http_code}" -X POST "$GATEWAY_URL/api/auth/login" \
    -H "Content-Type: application/json" \
    -d '{"email": "wrong@example.com", "password": "wrong"}' 2>/dev/null)
if [ "$INVALID_LOGIN_CODE" = "401" ]; then
    log_pass "Invalid login returns 401"
else
    log_fail "Invalid login" "Expected 401, got $INVALID_LOGIN_CODE"
fi

# Empty aggregator search
EMPTY_SEARCH=$(curl -sf "$AGGREGATOR_URL/api/v1/content?search=zzzzzznonexistent99999" 2>/dev/null)
if [ -n "$EMPTY_SEARCH" ]; then
    EMPTY_COUNT=$(echo "$EMPTY_SEARCH" | python3 -c "import sys,json; d=json.load(sys.stdin); print(d.get('total_elements', d.get('totalElements', 0)))" 2>/dev/null || echo "0")
    if [ "${EMPTY_COUNT:-0}" -eq 0 ] 2>/dev/null; then
        log_pass "Empty search returns 0 results"
    else
        log_fail "Empty search" "Expected 0 results, got $EMPTY_COUNT"
    fi
else
    log_fail "Empty search" "No response"
fi

# ============================================================
# 10. Database Stats
# ============================================================
log_info "=== Phase 10: Database Statistics ==="

echo ""
echo "--- content_agg_db ---"
docker exec skd-postgres psql -U postgres -d content_agg_db -c "
SELECT 'raw_content' as table_name, COUNT(*) as count FROM data_flow.raw_content
UNION ALL SELECT 'published_content', COUNT(*) FROM data_flow.published_content
UNION ALL SELECT 'articles (dedup)', COUNT(*) FROM data_flow.articles
UNION ALL SELECT 'similarities', COUNT(*) FROM data_flow.similarities
UNION ALL SELECT 'posts_features (rec)', COUNT(*) FROM data_flow.posts_features
UNION ALL SELECT 'rec_profiles', COUNT(*) FROM data_flow.rec_profiles
UNION ALL SELECT 'sources', COUNT(*) FROM config.sources
ORDER BY table_name;
" 2>/dev/null

echo ""
echo "--- Backend DBs ---"
docker exec skd-postgres psql -U postgres -d auth_db -c "SELECT 'auth.users' as table_name, COUNT(*) as count FROM users;" 2>/dev/null
docker exec skd-postgres psql -U postgres -d user_db -c "SELECT 'user.profiles' as table_name, COUNT(*) as count FROM profiles;" 2>/dev/null || echo "user.profiles: table may not exist"

# ============================================================
# Summary
# ============================================================
echo ""
echo "============================================================"
echo -e "  E2E Test Summary: ${GREEN}$PASS passed${NC}, ${RED}$FAIL failed${NC} out of $TOTAL tests"
echo "============================================================"

if [ ${#ISSUES[@]} -gt 0 ]; then
    echo ""
    echo "Issues found:"
    for issue in "${ISSUES[@]}"; do
        echo -e "  ${RED}•${NC} $issue"
    done
fi

echo ""
exit $FAIL
