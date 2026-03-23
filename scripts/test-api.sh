
#!/bin/bash
# InvoiceGenie API Test Script
# Usage: ./scripts/test-api.sh [base_url]
# Requires: curl; optional: jq (otherwise python3 or sed-based JSON field extraction is used)
# Default port 8080 works for both PostgreSQL (default) and SQLite profiles

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
echo "=== 19. Payment Allocation - FIFO Auto-Allocate (404 without payment) ==="
test_endpoint "FIFO Allocate" "POST" "/api/v1/payments/00000000-0000-0000-0000-000000000000/allocate/fifo" '{"allocatedBy":"00000000-0000-0000-0000-000000000001"}' "404"

echo ""
echo "=== 20. Payment Allocation - Manual Allocate (404 without payment) ==="
test_endpoint "Manual Allocate" "POST" "/api/v1/payments/00000000-0000-0000-0000-000000000000/allocate/manual" '{"allocatedBy":"00000000-0000-0000-0000-000000000001","allocations":[{"invoiceId":"00000000-0000-0000-0000-000000000000","amount":100.00,"notes":"test"}]}' "404"

echo ""
echo "=== 21. Payment Allocation - Get Allocations (404 without payment) ==="
test_endpoint "Get Allocations" "GET" "/api/v1/payments/00000000-0000-0000-0000-000000000000/allocations" "" "404"

echo ""
echo "=== 22. Payment Allocation - Get Invoice Allocations ==="
test_endpoint "Get Invoice Allocations" "GET" "/api/v1/payments/invoices/$INVOICE_ID/allocations" "" "200"

echo ""
echo "=== 23. Ledger - List Accounts ==="
test_endpoint "List Accounts" "GET" "/api/v1/ledger/accounts" "" "200"

echo ""
echo "=== 24. Ledger - Get Account Balance ==="
test_endpoint "Get AR Balance" "GET" "/api/v1/ledger/balance/AR" "" "200"

echo ""
echo "=== 25. Ledger - Get Transaction (404 without transaction) ==="
test_endpoint "Get Transaction" "GET" "/api/v1/ledger/transactions/00000000-0000-0000-0000-000000000000" "" "404"

echo ""
echo "=== 26. Ledger - Get By Reference ==="
test_endpoint "Get By Reference" "GET" "/api/v1/ledger/reference/INVOICE/$INVOICE_ID" "" "200"

echo ""
echo "=== 27. Cheque - Create (RECEIVED state) ==="
CHEQUE_CREATE='{
  "chequeNumber": "CHQ-'$(date +%s)'",
  "customerId": "11111111-1111-1111-1111-111111111111",
  "amount": 1000.00,
  "currencyCode": "USD",
  "bankName": "Test Bank",
  "bankBranch": "Main Branch",
  "chequeDate": "2026-03-20",
  "notes": "Test cheque"
}'
CHEQUE_CREATE_RESP=$(curl -s -w "%{http_code}" -X POST \
    -H "Content-Type: application/json" \
    -H "X-Tenant-Id: $TENANT_ID" \
    -d "$CHEQUE_CREATE" \
    "$BASE_URL/api/v1/cheques")
CHEQUE_CREATE_CODE="${CHEQUE_CREATE_RESP: -3}"
CHEQUE_CREATE_JSON="${CHEQUE_CREATE_RESP%???}"
echo -n "[Create Cheque] POST /api/v1/cheques ... "
if [[ "$CHEQUE_CREATE_CODE" == "201" ]]; then
    echo -e "${GREEN}PASS${NC} ($CHEQUE_CREATE_CODE)"
    PASS=$((PASS + 1))
    CHEQUE_ID=$(json_get_id "$CHEQUE_CREATE_JSON")
else
    echo -e "${RED}FAIL${NC} (expected 201, got $CHEQUE_CREATE_CODE)"
    pretty_json "$CHEQUE_CREATE_JSON"
    FAIL=$((FAIL + 1))
    CHEQUE_ID=""
fi

echo ""
echo "=== 28. Cheque - Deposit (RECEIVED → DEPOSITED) ==="
test_endpoint "Deposit Cheque" "POST" "/api/v1/cheques/$CHEQUE_ID/deposit" "" "200"

echo ""
echo "=== 29. Cheque - Clear (DEPOSITED → CLEARED) ==="
test_endpoint "Clear Cheque" "POST" "/api/v1/cheques/$CHEQUE_ID/clear" "" "200"

echo ""
echo "=== 30. Cheque - Get by ID ==="
test_endpoint "Get Cheque" "GET" "/api/v1/cheques/$CHEQUE_ID" "" "200"

echo ""
echo "=== 31. Cheque - List ==="
test_endpoint "List Cheques" "GET" "/api/v1/cheques" "" "200"

echo ""
echo "=== 32. Cheque - Create for Bounce Test ==="
CHEQUE_BOUNCE_CREATE='{
  "chequeNumber": "CHQ-BOUNCE-'$(date +%s)'",
  "customerId": "11111111-1111-1111-1111-111111111111",
  "amount": 500.00,
  "currencyCode": "USD",
  "bankName": "Test Bank",
  "bankBranch": "Main Branch",
  "chequeDate": "2026-03-20",
  "notes": "Cheque for bounce test"
}'
CHEQUE_BOUNCE_RESP=$(curl -s -w "%{http_code}" -X POST \
    -H "Content-Type: application/json" \
    -H "X-Tenant-Id: $TENANT_ID" \
    -d "$CHEQUE_BOUNCE_CREATE" \
    "$BASE_URL/api/v1/cheques")
CHEQUE_BOUNCE_CODE="${CHEQUE_BOUNCE_RESP: -3}"
CHEQUE_BOUNCE_JSON="${CHEQUE_BOUNCE_RESP%???}"
if [[ "$CHEQUE_BOUNCE_CODE" == "201" ]]; then
    CHEQUE_BOUNCE_ID=$(json_get_id "$CHEQUE_BOUNCE_JSON")
    echo -e "${GREEN}PASS${NC} (201)"
    PASS=$((PASS + 1))
else
    echo -e "${RED}FAIL${NC} (expected 201, got $CHEQUE_BOUNCE_CODE)"
    FAIL=$((FAIL + 1))
    CHEQUE_BOUNCE_ID=""
fi

echo ""
echo "=== 33. Cheque - Deposit for Bounce Test ==="
if [ -n "$CHEQUE_BOUNCE_ID" ]; then
    test_endpoint "Deposit Bounce Cheque" "POST" "/api/v1/cheques/$CHEQUE_BOUNCE_ID/deposit" "" "200"
else
    echo -e "${YELLOW}SKIP${NC} (no cheque ID)"
fi

echo ""
echo "=== 34. Cheque - Bounce (DEPOSITED → BOUNCED) ==="
if [ -n "$CHEQUE_BOUNCE_ID" ]; then
    test_endpoint "Bounce Cheque" "POST" "/api/v1/cheques/$CHEQUE_BOUNCE_ID/bounce" '{"reason":"Insufficient funds"}' "200"
else
    echo -e "${YELLOW}SKIP${NC} (no cheque ID)"
fi

echo ""
echo "=== 35. Cheque - Get Bounced Cheque ==="
if [ -n "$CHEQUE_BOUNCE_ID" ]; then
    test_endpoint "Get Bounced Cheque" "GET" "/api/v1/cheques/$CHEQUE_BOUNCE_ID" "" "200"
else
    echo -e "${YELLOW}SKIP${NC} (no cheque ID)"
fi

echo ""
echo "=== 36. Aging - Get Aging Report ==="
test_endpoint "Aging Report" "GET" "/api/v1/aging" "" "200"

echo ""
echo "=== 37. Aging - Get Buckets ==="
test_endpoint "Aging Buckets" "GET" "/api/v1/aging/buckets" "" "200"

echo ""
echo "=== 38. Aging - Calculate Discount ==="
test_endpoint "Calculate Discount" "POST" "/api/v1/aging/discount/calculate" '{"amount": 1000.00, "currencyCode": "USD", "dueDate": "2026-04-30"}' "200"

echo ""
echo "=== 39. Credit Notes - Generate ==="
test_endpoint "Generate Credit Note" "POST" "/api/v1/credit-notes" '{"customerId": "11111111-1111-1111-1111-111111111111", "discountAmount": 20.00, "currencyCode": "USD"}' "201"

echo ""
echo "=== 40. Credit Notes - List ==="
test_endpoint "List Credit Notes" "GET" "/api/v1/credit-notes" "" "200"

echo ""
echo "=========================================="
echo -e "Results: ${GREEN}$PASS passed${NC}, ${RED}$FAIL failed${NC}"
echo "=========================================="

exit $FAIL
