#!/bin/bash
# InvoiceGenie API Test Script
# Usage: ./scripts/test-api.sh [base_url]
# Requires: curl, jq

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
        echo "$body" | jq -r '.id' 2>/dev/null || true
    else
        echo -e "${RED}FAIL${NC} (expected $expected, got $http_code)"
        echo "$body" | jq '.' 2>/dev/null || echo "$body"
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
test_endpoint "Create" "POST" "/api/v1/invoices" "$CREATE_BODY" "201"

# Extract ID from last response (simplified - in real script would parse)
INVOICE_ID=$(curl -s -X POST -H "Content-Type: application/json" -H "X-Tenant-Id: $TENANT_ID" -d "$CREATE_BODY" "$BASE_URL/api/v1/invoices" | jq -r '.id' 2>/dev/null || echo "test-id")

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
INVOICE2_ID=$(curl -s -X POST -H "Content-Type: application/json" -H "X-Tenant-Id: $TENANT_ID" -d "$CREATE2" "$BASE_URL/api/v1/invoices" | jq -r '.id' 2>/dev/null || echo "test2")

echo ""
echo "=== 12. Mark Overdue (with past due date) ==="
test_endpoint "Overdue Past" "POST" "/api/v1/invoices/$INVOICE2_ID/overdue?today=2026-01-01" "" "200"

echo ""
echo "=== 13. Write Off ==="
test_endpoint "WriteOff" "POST" "/api/v1/invoices/$INVOICE2_ID/writeoff" '{"reason":"Bad debt"}' "200"

echo ""
echo "=== 14. Pagination Test ==="
test_endpoint "Page 1" "GET" "/api/v1/invoices?limit=1" "" "200"
test_endpoint "Page 2" "GET" "/api/v1/invoices?limit=1&cursor=test" "" "200"

echo ""
echo "=== 15. Idempotency Header (ignored for now) ==="
test_endpoint "Idempotent" "POST" "/api/v1/invoices" "$CREATE_BODY" "400"

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
