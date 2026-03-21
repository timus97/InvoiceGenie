#!/bin/bash
# InvoiceGenie API Test Script
# Usage: ./scripts/test-api.sh [base_url]
# Requires: curl; optional: jq (otherwise python3 or sed-based JSON field extraction is used)

set -e

BASE_URL="${1:-http://localhost:8080}"
TENANT_ID="${TENANT_ID:-11111111-1111-1111-1111-111111111111}"

echo "=========================================="
echo "InvoiceGenie API Test Suite"
echo "Base URL: $BASE_URL"
echo "Tenant: $TENANT_ID"
echo "=========================================="

# Colors
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m'

# --- JSON helpers (work without jq) ---
json_get_id() {
    local json="$1"
    if command -v jq >/dev/null 2>&1; then
        echo "$json" | jq -r '.id // empty' 2>/dev/null || true
    elif command -v python3 >/dev/null 2>&1; then
        echo "$json" | python3 -c "import sys,json; print(json.load(sys.stdin).get('id') or '')" 2>/dev/null || true
    else
        echo "$json" | sed -n 's/.*"id"[[:space:]]*:[[:space:]]*"\([^"]*\)".*/\1/p'
    fi
}

json_get_next_cursor() {
    local json="$1"
    if command -v jq >/dev/null 2>&1; then
        echo "$json" | jq -r '.nextCursor // empty' 2>/dev/null || true
    elif command -v python3 >/dev/null 2>&1; then
        echo "$json" | python3 -c "import sys,json; v=json.load(sys.stdin).get('nextCursor'); print('' if v is None else v)" 2>/dev/null || true
    else
        echo "$json" | sed -n 's/.*"nextCursor"[[:space:]]*:[[:space:]]*"\([^"]*\)".*/\1/p'
    fi
}

pretty_json() {
    local json="$1"
    if command -v jq >/dev/null 2>&1; then
        echo "$json" | jq '.'
    elif command -v python3 >/dev/null 2>&1; then
        echo "$json" | python3 -m json.tool 2>/dev/null || echo "$json"
    else
        echo "$json"
    fi
}

# Wait for server readiness
echo -n "Waiting for server..."
for i in $(seq 1 30); do
    if curl -s -o /dev/null -w "%{http_code}" "$BASE_URL/q/health" | grep -q "200"; then
        echo " ready"
        break
    fi
    echo -n "."
    sleep 1
done

PASS=0
FAIL=0

test_endpoint() {
    local name="$1"
    local method="$2"
    local path="$3"
    local data="$4"
    local expected="$5"

    echo -n "[$name] $method $path ... "

    if [ -n "$data" ]; then
        response=$(curl -s -w "%{http_code}" -X "$method" \
            -H "Content-Type: application/json" \
            -H "X-Tenant-Id: $TENANT_ID" \
            -d "$data" \
            "$BASE_URL$path")
    else
        response=$(curl -s -w "%{http_code}" -X "$method" \
            -H "Content-Type: application/json" \
            -H "X-Tenant-Id: $TENANT_ID" \
            "$BASE_URL$path")
    fi

    http_code="${response: -3}"
    body="${response%???}"

    if [[ "$http_code" == "$expected" ]]; then
        echo -e "${GREEN}PASS${NC} ($http_code)"
        PASS=$((PASS + 1))
        id_val=$(json_get_id "$body" || true)
        if [ -n "$id_val" ]; then echo "$id_val"; fi
    else
        echo -e "${RED}FAIL${NC} (expected $expected, got $http_code)"
        pretty_json "$body"
        FAIL=$((FAIL + 1))
    fi
}

echo ""
echo "=== 1. Health Check ==="
test_endpoint "Health" "GET" "/q/health" "" "200"

echo ""
echo "=== 2. Swagger UI ==="
test_endpoint "OpenAPI" "GET" "/q/openapi" "" "200"

echo ""
echo "=== 3. Create Invoice (with lines) ==="
CREATE_BODY='{
  "invoiceNumber": "INV-TEST-'$(date +%s)'",
  "customerRef": "TEST-CUST",
  "currencyCode": "USD",
  "dueDate": "2026-04-30",
  "lines": [
    {"description": "Consulting", "amount": 500.00},
    {"description": "Expenses", "amount": 50.00}
  ]
}'
CREATE_RESP=$(curl -s -w "%{http_code}" -X POST \
    -H "Content-Type: application/json" \
    -H "X-Tenant-Id: $TENANT_ID" \
    -d "$CREATE_BODY" \
    "$BASE_URL/api/v1/invoices")
CREATE_CODE="${CREATE_RESP: -3}"
CREATE_JSON="${CREATE_RESP%???}"
echo -n "[Create] POST /api/v1/invoices ... "
if [[ "$CREATE_CODE" == "201" ]]; then
    echo -e "${GREEN}PASS${NC} ($CREATE_CODE)"
    PASS=$((PASS + 1))
    INVOICE_ID=$(json_get_id "$CREATE_JSON")
else
    echo -e "${RED}FAIL${NC} (expected 201, got $CREATE_CODE)"
    pretty_json "$CREATE_JSON"
    FAIL=$((FAIL + 1))
    INVOICE_ID=""
fi

echo ""
echo "=== 4. Get Invoice ==="
test_endpoint "Get" "GET" "/api/v1/invoices/$INVOICE_ID" "" "200"

echo ""
echo "=== 5. List Invoices ==="
test_endpoint "List" "GET" "/api/v1/invoices?limit=10" "" "200"

echo ""
echo "=== 6. List with Status Filter ==="
test_endpoint "List Filtered" "GET" "/api/v1/invoices?status=ISSUED&limit=5" "" "200"

echo ""
echo "=== 7. Issue Invoice (already issued via create, expect 409) ==="
test_endpoint "Issue" "POST" "/api/v1/invoices/$INVOICE_ID/issue" "" "409"

echo ""
echo "=== 8. Apply Partial Payment ==="
test_endpoint "Payment Partial" "POST" "/api/v1/invoices/$INVOICE_ID/payment" '{"fullyPaid": false}' "200"

echo ""
echo "=== 9. Apply Full Payment ==="
test_endpoint "Payment Full" "POST" "/api/v1/invoices/$INVOICE_ID/payment" '{"fullyPaid": true}' "200"

echo ""
echo "=== 10. Mark Overdue (should fail - already PAID) ==="
test_endpoint "Overdue" "POST" "/api/v1/invoices/$INVOICE_ID/overdue" "" "409"

echo ""
echo "=== 11. Create Second Invoice for Lifecycle Tests ==="
CREATE2='{"invoiceNumber":"INV-OVERDUE-'$(date +%s)'","customerRef":"C2","currencyCode":"USD","dueDate":"2020-01-01","lines":[{"description":"Old","amount":100}]}'
CREATE2_RESP=$(curl -s -w "%{http_code}" -X POST \
    -H "Content-Type: application/json" \
    -H "X-Tenant-Id: $TENANT_ID" \
    -d "$CREATE2" \
    "$BASE_URL/api/v1/invoices")
CREATE2_CODE="${CREATE2_RESP: -3}"
CREATE2_JSON="${CREATE2_RESP%???}"
if [[ "$CREATE2_CODE" == "201" ]]; then
    INVOICE2_ID=$(json_get_id "$CREATE2_JSON")
else
    INVOICE2_ID=""
fi

echo ""
echo "=== 12. Mark Overdue (with past due date) ==="
test_endpoint "Overdue Past" "POST" "/api/v1/invoices/$INVOICE2_ID/overdue?today=2026-01-01" "" "200"

echo ""
echo "=== 13. Write Off ==="
test_endpoint "WriteOff" "POST" "/api/v1/invoices/$INVOICE2_ID/writeoff" '{"reason":"Bad debt"}' "200"

echo ""
echo "=== 14. Pagination Test ==="
PAGE1=$(curl -s -X GET -H "X-Tenant-Id: $TENANT_ID" "$BASE_URL/api/v1/invoices?limit=1")
if command -v python3 >/dev/null 2>&1; then
    echo "$PAGE1" | python3 -c "import sys,json; d=json.load(sys.stdin); print(d['items'][0]['id'] if d.get('items') else '')" 2>/dev/null || true
fi
NEXT=$(json_get_next_cursor "$PAGE1")
test_endpoint "Page 1" "GET" "/api/v1/invoices?limit=1" "" "200"
if [ -n "$NEXT" ]; then
    test_endpoint "Page 2" "GET" "/api/v1/invoices?limit=1&cursor=$NEXT" "" "200"
else
    echo -e "${YELLOW}SKIP${NC} (no nextCursor)"
fi

echo ""
echo "=== 15. Idempotency Header (ignored for now) ==="
CREATE15='{
  "invoiceNumber": "INV-TEST-IDEM-'$(date +%s)'",
  "customerRef": "TEST-CUST",
  "currencyCode": "USD",
  "dueDate": "2026-04-30",
  "lines": [
    {"description": "Consulting", "amount": 500.00},
    {"description": "Expenses", "amount": 50.00}
  ]
}'
test_endpoint "Idempotent" "POST" "/api/v1/invoices" "$CREATE15" "201"

echo ""
echo "=== 16. Validation Error (no lines) ==="
test_endpoint "No Lines" "POST" "/api/v1/invoices" '{"invoiceNumber":"X"}' "400"

echo ""
echo "=== 17. 404 Not Found ==="
test_endpoint "NotFound" "GET" "/api/v1/invoices/00000000-0000-0000-0000-000000000000" "" "404"

echo ""
echo "=== 18. 405 Delete ==="
test_endpoint "Delete" "DELETE" "/api/v1/invoices/$INVOICE_ID" "" "405"

echo ""
echo "=========================================="
echo -e "Results: ${GREEN}$PASS passed${NC}, ${RED}$FAIL failed${NC}"
echo "=========================================="

exit $FAIL
