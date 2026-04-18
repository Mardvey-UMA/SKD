#!/bin/bash
# Bulk-add sources from links.json to config-service via port-forwarded API.
# Usage: ./add-sources.sh [habr|vc|all]
# Prereq: kubectl port-forward -n skd svc/config-service 18081:8081

set -e
TARGET="${1:-all}"
API="${CONFIG_API:-http://localhost:18081/api/config/v1/sources}"
LINKS="${LINKS:-/home/mattew/SKD/links.json}"

add_habr() {
  local url="$1"
  local alias_name=$(basename "$url" | sed 's/\///g')
  [ -z "$alias_name" ] && alias_name=$(echo "$url" | awk -F/ '{print $(NF-1)}')
  local name="Habr ${alias_name}"
  local code=$(curl -s -o /tmp/add-src.out -w "%{http_code}" -X POST "$API/habr" \
    -H "Content-Type: application/json" \
    -d "$(jq -n --arg a "$url" --arg n "$name" '{alias:$a, sourceType:"HABR", name:$n, parseComments:false, parseImages:true, maxArticles:30, updateFrequencyMinutes:60, isActive:true}')")
  local existing=$(jq -r '.was_existing // "?"' </tmp/add-src.out 2>/dev/null)
  echo "[habr] $alias_name  HTTP $code  was_existing=$existing"
}

add_vc() {
  local url="$1"
  local alias_name=$(basename "$url")
  local name="VC ${alias_name}"
  local code=$(curl -s -o /tmp/add-src.out -w "%{http_code}" -X POST "$API/vcru" \
    -H "Content-Type: application/json" \
    -d "$(jq -n --arg a "$alias_name" --arg n "$name" '{alias:$a, sourceType:"VCRU", name:$n, maxArticles:30, parseImages:true, sorting:"new", updateFrequencyMinutes:60, isActive:true}')")
  local existing=$(jq -r '.was_existing // "?"' </tmp/add-src.out 2>/dev/null)
  echo "[vc]   $alias_name  HTTP $code  was_existing=$existing"
}

add_tg() {
  local channel="$1"
  # Use generic endpoint (bypass /telegram POST which triggers TG validation
  # call to parser-tg — that has an unresolved Jackson Kotlin deserialization
  # bug against TelegramValidateRequest). Generic endpoint accepts typed
  # parameters map and creates the source directly.
  local code=$(curl -s -o /tmp/add-src.out -w "%{http_code}" -X POST "$API" \
    -H "Content-Type: application/json" \
    -d "$(jq -n --arg c "$channel" '{sourceType:"TELEGRAM", name:$c, isActive:true, updateFrequencyMinutes:30, parameters:{channelUsername:$c, downloadMedia:"true", maxMessages:"50", maxMediaSizeMb:"50", batchSize:"50"}}')")
  local existing=$(jq -r '.was_existing // "?"' </tmp/add-src.out 2>/dev/null)
  echo "[tg]   $channel  HTTP $code  was_existing=$existing"
}

if [ "$TARGET" = "habr" ] || [ "$TARGET" = "all" ]; then
  echo "=== Adding Habr sources ==="
  jq -r '.habr[]' "$LINKS" | while read url; do add_habr "$url"; done
fi

if [ "$TARGET" = "vc" ] || [ "$TARGET" = "all" ]; then
  echo "=== Adding VC.RU sources ==="
  jq -r '.vc[]' "$LINKS" | while read url; do add_vc "$url"; done
fi

if [ "$TARGET" = "tg" ] || [ "$TARGET" = "all" ]; then
  echo "=== Adding Telegram channels ==="
  jq -r '.tg[]' "$LINKS" | while read ch; do add_tg "$ch"; done
fi

echo ""
echo "=== Final counts ==="
curl -s "$API/habr" | jq 'length' | xargs -I {} echo "Habr: {} sources"
curl -s "$API/vcru" | jq 'length' | xargs -I {} echo "VC:   {} sources"
