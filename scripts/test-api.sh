#!/bin/bash
# InvoiceGenie API Test Script - Comprehensive
# Tests: Customer, Invoice, Cheque, CreditNote, Outbox workflows
# Usage: ./scripts/test-api.sh [base_url]

set -e

BASE_URL="${1:-http://localhost:8080}"
TENANT_ID="${TENANT_ID:-11111111-1111-1111-1111-111111111111}"

echo "=========================================="
echo "InvoiceGenie Comprehensive API Test Suite"
echo "Base URL: $BASE_URL"
echo "Tenant: $TENANT_ID"
echo "=========================================="

# Colors
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
NC='\033[0m'

# --- JSON helpers (work without jq) ---
json_get() {
    local json="$1"
    local field="$2"
    if command -v jq >/dev/null 2>&1; then
        echo "$json" | jq -r ".$field // empty" 2>/dev/null || true
    elif command -v python3 >/dev/null 2>&1; then
        echo "$json" | python3 -c "import sys,json; print(json.load(sys.stdin).get('$field') or '')" 2>/dev/null || true
    else
        echo "$json" | sed -n "s/.*\"$field\"[[:space:]]*:[[:space:]]*\"\\([^\"]*\\)\".*/\\1/p"
    fi
}

json_get_id() {
    json_get "$1" "id"
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
        if [ -n "$id_val" ]; then echo "  ID: $id_val"; fi
    else
        echo -e "${RED}FAIL${NC} (expected $expected, got $http_code)"
        pretty_json "$body"
        FAIL=$((FAIL + 1))
    fi
}

# Store IDs for later tests
CUSTOMER_ID=""
INVOICE_ID=""
CHEQUE_ID=""
CREDIT_NOTE_ID=""

echo ""
echo -e "${BLUE}========================================${NC}"
echo -e "${BLUE}   SECTION 1: CUSTOMER WORKFLOW${NC}"
echo -e "${BLUE}========================================${NC}"

echo ""
echo "=== 1.1 Create Customer ==="
CREATE_CUSTOMER='{"customerCode":"CUST-'$(date +%s)'","legalName":"Test Customer Inc.","currency":"USD"}'
CREATE_CUSTOMER_RESP=$(curl -s -w "%{http_code}" -X POST \
    -H "Content-Type: application/json" \
    -H "X-Tenant-Id: $TENANT_ID" \
    -d "$CREATE_CUSTOMER" \
    "$BASE_URL/api/v1/customers")
CREATE_CUSTOMER_CODE="${CREATE_CUSTOMER_RESP: -3}"
CREATE_CUSTOMER_JSON="${CREATE_CUSTOMER_RESP%???}"
echo -n "[Create Customer] POST /api/v1/customers ... "
if [[ "$CREATE_CUSTOMER_CODE" == "201" ]]; then
    echo -e "${GREEN}PASS${NC} ($CREATE_CUSTOMER_CODE)"
    PASS=$((PASS + 1))
    CUSTOMER_ID=$(json_get_id "$CREATE_CUSTOMER_JSON")
    echo "  Customer ID: $CUSTOMER_ID"
else
    echo -e "${RED}FAIL${NC} (expected 201, got $CREATE_CUSTOMER_CODE)"
    pretty_json "$CREATE_CUSTOMER_JSON"
    FAIL=$((FAIL + 1))
fi

echo ""
echo "=== 1.2 Get Customer ==="
test_endpoint "Get Customer" "GET" "/api/v1/customers/$CUSTOMER_ID" "" "200"

echo ""
echo "=== 1.3 List Customers ==="
test_endpoint "List Customers" "GET" "/api/v1/customers" "" "200"

echo ""
echo "=== 1.4 Update Customer ==="
test_endpoint "Update Customer" "PUT" "/api/v1/customers/$CUSTOMER_ID" \
    '{"displayName":"Test Customer Updated","email":"test@example.com","creditLimit":10000.00}' "200"

echo ""
echo "=== 1.5 Credit Limit Check (should pass) ==="
test_endpoint "Credit Check OK" "GET" "/api/v1/customers/$CUSTOMER_ID/credit-check?outstanding=5000&invoiceAmount=4000" "" "200"

echo ""
echo "=== 1.6 Credit Limit Check (should fail - over limit) ==="
test_endpoint "Credit Check Over" "GET" "/api/v1/customers/$CUSTOMER_ID/credit-check?outstanding=9000&invoiceAmount=2000" "" "200"

echo ""
echo "=== 1.7 Customer Stats ==="
test_endpoint "Customer Stats" "GET" "/api/v1/customers/stats" "" "200"

echo ""
echo "=== 1.8 Search Customers ==="
test_endpoint "Search Customers" "GET" "/api/v1/customers?search=Test" "" "200"

echo ""
echo "=== 1.9 Block Customer ==="
test_endpoint "Block Customer" "POST" "/api/v1/customers/$CUSTOMER_ID/block" "" "200"

echo ""
echo "=== 1.10 Unblock Customer ==="
test_endpoint "Unblock Customer" "POST" "/api/v1/customers/$CUSTOMER_ID/unblock" "" "200"

echo ""
echo -e "${BLUE}========================================${NC}"
echo -e "${BLUE}   SECTION 2: INVOICE WORKFLOW${NC}"
echo -e "${BLUE}========================================${NC}"

echo ""
echo "=== 2.1 Create Invoice ==="
CREATE_INVOICE='{
  "invoiceNumber": "INV-TEST-'$(date +%s)'",
  "customerRef": "'$CUSTOMER_ID'",
  "currencyCode": "USD",
  "dueDate": "2026-04-30",
  "lines": [
    {"description": "Consulting Services", "amount": 5000.00},
    {"description": "Expenses", "amount": 500.00}
  ]
}'
CREATE_INV_RESP=$(curl -s -w "%{http_code}" -X POST \
    -H "Content-Type: application/json" \
    -H "X-Tenant-Id: $TENANT_ID" \
    -d "$CREATE_INVOICE" \
    "$BASE_URL/api/v1/invoices")
CREATE_INV_CODE="${CREATE_INV_RESP: -3}"
CREATE_INV_JSON="${CREATE_INV_RESP%???}"
echo -n "[Create Invoice] POST /api/v1/invoices ... "
if [[ "$CREATE_INV_CODE" == "201" ]]; then
    echo -e "${GREEN}PASS${NC} ($CREATE_INV_CODE)"
    PASS=$((PASS + 1))
    INVOICE_ID=$(json_get_id "$CREATE_INV_JSON")
    echo "  Invoice ID: $INVOICE_ID"
else
    echo -e "${RED}FAIL${NC} (expected 201, got $CREATE_INV_CODE)"
    pretty_json "$CREATE_INV_JSON"
    FAIL=$((FAIL + 1))
fi

echo ""
echo "=== 2.2 Get Invoice ==="
test_endpoint "Get Invoice" "GET" "/api/v1/invoices/$INVOICE_ID" "" "200"

echo ""
echo "=== 2.3 List Invoices ==="
test_endpoint "List Invoices" "GET" "/api/v1/invoices?limit=10" "" "200"

echo ""
echo "=== 2.4 Apply Payment ==="
test_endpoint "Payment" "POST" "/api/v1/invoices/$INVOICE_ID/payment" '{"fullyPaid": true}' "200"

echo ""
echo -e "${BLUE}========================================${NC}"
echo -e "${BLUE}   SECTION 3: CHEQUE WORKFLOW${NC}"
echo -e "${BLUE}========================================${NC}"

echo ""
echo "=== 3.1 Create Cheque (RECEIVED) ==="
CREATE_CHEQUE='{
  "chequeNumber": "CHQ-'$(date +%s)'",
  "customerId": "'$CUSTOMER_ID'",
  "amount": 1000.00,
  "currencyCode": "USD",
  "bankName": "Test Bank",
  "bankBranch": "Main Branch",
  "chequeDate": "2026-03-20",
  "notes": "Test cheque for workflow"
}'
CREATE_CHQ_RESP=$(curl -s -w "%{http_code}" -X POST \
    -H "Content-Type: application/json" \
    -H "X-Tenant-Id: $TENANT_ID" \
    -d "$CREATE_CHEQUE" \
    "$BASE_URL/api/v1/cheques")
CREATE_CHQ_CODE="${CREATE_CHQ_RESP: -3}"
CREATE_CHQ_JSON="${CREATE_CHQ_RESP%???}"
echo -n "[Create Cheque] POST /api/v1/cheques ... "
if [[ "$CREATE_CHQ_CODE" == "201" ]]; then
    echo -e "${GREEN}PASS${NC} ($CREATE_CHQ_CODE)"
    PASS=$((PASS + 1))
    CHEQUE_ID=$(json_get_id "$CREATE_CHQ_JSON")
    echo "  Cheque ID: $CHEQUE_ID"
else
    echo -e "${RED}FAIL${NC} (expected 201, got $CREATE_CHQ_CODE)"
    pretty_json "$CREATE_CHQ_JSON"
    FAIL=$((FAIL + 1))
fi

echo ""
echo "=== 3.2 Get Cheque ==="
test_endpoint "Get Cheque" "GET" "/api/v1/cheques/$CHEQUE_ID" "" "200"

echo ""
echo "=== 3.3 Deposit Cheque (RECEIVED → DEPOSITED) ==="
test_endpoint "Deposit Cheque" "POST" "/api/v1/cheques/$CHEQUE_ID/deposit" "" "200"

echo ""
echo "=== 3.4 Create Second Cheque for Bounce Test ==="
CREATE_CHQ2='{
  "chequeNumber": "CHQ-BOUNCE-'$(date +%s)'",
  "customerId": "'$CUSTOMER_ID'",
  "amount": 500.00,
  "currencyCode": "USD",
  "bankName": "Bad Bank",
  "bankBranch": "Branch 2",
  "chequeDate": "2026-03-20",
  "notes": "Cheque for bounce test"
}'
CREATE_CHQ2_RESP=$(curl -s -w "%{http_code}" -X POST \
    -H "Content-Type: application/json" \
    -H "X-Tenant-Id: $TENANT_ID" \
    -d "$CREATE_CHQ2" \
    "$BASE_URL/api/v1/cheques")
CREATE_CHQ2_CODE="${CREATE_CHQ2_RESP: -3}"
CREATE_CHQ2_JSON="${CREATE_CHQ2_RESP%???}"
CHEQUE2_ID=""
if [[ "$CREATE_CHQ2_CODE" == "201" ]]; then
    CHEQUE2_ID=$(json_get_id "$CREATE_CHQ2_JSON")
    echo -e "${GREEN}PASS${NC} (201) - Cheque2 ID: $CHEQUE2_ID"
    PASS=$((PASS + 1))
else
    echo -e "${RED}FAIL${NC} (expected 201, got $CREATE_CHQ2_CODE)"
    FAIL=$((FAIL + 1))
fi

echo ""
echo "=== 3.5 Deposit Second Cheque ==="
test_endpoint "Deposit Cheque2" "POST" "/api/v1/cheques/$CHEQUE2_ID/deposit" "" "200"

echo ""
echo "=== 3.6 Bounce Second Cheque (DEPOSITED → BOUNCED) ==="
test_endpoint "Bounce Cheque" "POST" "/api/v1/cheques/$CHEQUE2_ID/bounce" '{"reason":"Insufficient funds"}' "200"

echo ""
echo "=== 3.7 Clear First Cheque (DEPOSITED → CLEARED) ==="
test_endpoint "Clear Cheque" "POST" "/api/v1/cheques/$CHEQUE_ID/clear" "" "200"

echo ""
echo "=== 3.8 List Cheques by Status ==="
test_endpoint "List Cleared Cheques" "GET" "/api/v1/cheques?status=CLEARED" "" "200"

echo ""
echo -e "${BLUE}========================================${NC}"
echo -e "${BLUE}   SECTION 4: CREDIT NOTE WORKFLOW${NC}"
echo -e "${BLUE}========================================${NC}"

echo ""
echo "=== 4.1 Generate Credit Note (Early Payment Discount) ==="
CREATE_CN='{
  "customerId": "'$CUSTOMER_ID'",
  "discountAmount": 100.00,
  "currencyCode": "USD",
  "referenceInvoiceId": "'$INVOICE_ID'"
}'
CREATE_CN_RESP=$(curl -s -w "%{http_code}" -X POST \
    -H "Content-Type: application/json" \
    -H "X-Tenant-Id: $TENANT_ID" \
    -d "$CREATE_CN" \
    "$BASE_URL/api/v1/credit-notes")
CREATE_CN_CODE="${CREATE_CN_RESP: -3}"
CREATE_CN_JSON="${CREATE_CN_RESP%???}"
echo -n "[Create Credit Note] POST /api/v1/credit-notes ... "
if [[ "$CREATE_CN_CODE" == "201" ]]; then
    echo -e "${GREEN}PASS${NC} ($CREATE_CN_CODE)"
    PASS=$((PASS + 1))
    CREDIT_NOTE_ID=$(json_get_id "$CREATE_CN_JSON")
    echo "  Credit Note ID: $CREDIT_NOTE_ID"
else
    echo -e "${RED}FAIL${NC} (expected 201, got $CREATE_CN_CODE)"
    pretty_json "$CREATE_CN_JSON"
    FAIL=$((FAIL + 1))
fi

echo ""
echo "=== 4.2 Get Credit Note ==="
test_endpoint "Get Credit Note" "GET" "/api/v1/credit-notes/$CREDIT_NOTE_ID" "" "200"

echo ""
echo "=== 4.3 List Credit Notes ==="
test_endpoint "List Credit Notes" "GET" "/api/v1/credit-notes" "" "200"

echo ""
echo "=== 4.4 List Issued Credit Notes ==="
test_endpoint "List Issued CN" "GET" "/api/v1/credit-notes?status=ISSUED" "" "200"

echo ""
echo -e "${BLUE}========================================${NC}"
echo -e "${BLUE}   SECTION 5: OUTBOX WORKFLOW${NC}"
echo -e "${BLUE}========================================${NC}"

echo ""
echo "=== 5.1 Outbox Stats ==="
test_endpoint "Outbox Stats" "GET" "/api/v1/outbox/stats" "" "200"

echo ""
echo "=== 5.2 Outbox Pending ==="
test_endpoint "Outbox Pending" "GET" "/api/v1/outbox/pending?limit=10" "" "200"

echo ""
echo "=== 5.3 Trigger Manual Processing ==="
test_endpoint "Process Outbox" "POST" "/api/v1/outbox/process" "" "200"

echo ""
echo "=== 5.4 Verify Processing ==="
sleep 2
OUTBOX_STATS=$(curl -s -X GET \
    -H "X-Tenant-Id: $TENANT_ID" \
    "$BASE_URL/api/v1/outbox/stats")
echo "Outbox stats after processing:"
pretty_json "$OUTBOX_STATS"

echo ""
echo -e "${BLUE}========================================${NC}"
echo -e "${BLUE}   SECTION 6: CUSTOMER DELETE WORKFLOW${NC}"
echo -e "${BLUE}========================================${NC}"

echo ""
echo "=== 6.1 Create Customer for Delete Test ==="
CREATE_DEL_CUST='{"customerCode":"CUST-DEL-'$(date +%s)'","legalName":"Customer To Delete","currency":"USD"}'
CREATE_DEL_RESP=$(curl -s -w "%{http_code}" -X POST \
    -H "Content-Type: application/json" \
    -H "X-Tenant-Id: $TENANT_ID" \
    -d "$CREATE_DEL_CUST" \
    "$BASE_URL/api/v1/customers")
DEL_CUST_ID=""
if [[ "${CREATE_DEL_RESP: -3}" == "201" ]]; then
    DEL_CUST_ID=$(json_get_id "${CREATE_DEL_RESP%???}")
    echo -e "${GREEN}PASS${NC} (201) - Created: $DEL_CUST_ID"
    PASS=$((PASS + 1))
else
    echo -e "${RED}FAIL${NC}"
    FAIL=$((FAIL + 1))
fi

echo ""
echo "=== 6.2 Delete Customer (Soft Delete) ==="
test_endpoint "Delete Customer" "DELETE" "/api/v1/customers/$DEL_CUST_ID" "" "200"

echo ""
echo "=== 6.3 Verify Customer Status is DELETED ==="
DEL_CUST_RESP=$(curl -s -X GET \
    -H "X-Tenant-Id: $TENANT_ID" \
    "$BASE_URL/api/v1/customers/$DEL_CUST_ID")
DEL_STATUS=$(json_get "$DEL_CUST_RESP" "status")
echo "Customer status after delete: $DEL_STATUS"
if [[ "$DEL_STATUS" == "DELETED" ]]; then
    echo -e "${GREEN}PASS${NC} - Customer status is DELETED"
    PASS=$((PASS + 1))
else
    echo -e "${RED}FAIL${NC} - Expected DELETED, got $DEL_STATUS"
    FAIL=$((FAIL + 1))
fi

echo ""
echo "=== 6.4 List Customers (exclude deleted) ==="
test_endpoint "List Active Only" "GET" "/api/v1/customers?includeDeleted=false" "" "200"

echo ""
echo -e "${BLUE}========================================${NC}"
echo -e "${BLUE}   SECTION 7: ERROR CASES${NC}"
echo -e "${BLUE}========================================${NC}"

echo ""
echo "=== 7.1 Duplicate Customer Code (should fail) ==="
test_endpoint "Duplicate Code" "POST" "/api/v1/customers" "$CREATE_CUSTOMER" "400"

echo ""
echo "=== 7.2 Get Non-existent Customer (404) ==="
test_endpoint "Not Found Customer" "GET" "/api/v1/customers/00000000-0000-0000-0000-000000000000" "" "404"

echo ""
echo "=== 7.3 Invalid State Transition (clear RECEIVED cheque) ==="
CREATE_INVALID_CHQ='{
  "chequeNumber": "CHQ-INVALID-'$(date +%s)'",
  "customerId": "'$CUSTOMER_ID'",
  "amount": 100.00,
  "currencyCode": "USD",
  "bankName": "Test Bank",
  "bankBranch": "Branch",
  "chequeDate": "2026-03-20"
}'
INVALID_CHQ_RESP=$(curl -s -w "%{http_code}" -X POST \
    -H "Content-Type: application/json" \
    -H "X-Tenant-Id: $TENANT_ID" \
    -d "$CREATE_INVALID_CHQ" \
    "$BASE_URL/api/v1/cheques")
INVALID_CHQ_ID=""
if [[ "${INVALID_CHQ_RESP: -3}" == "201" ]]; then
    INVALID_CHQ_ID=$(json_get_id "${INVALID_CHQ_RESP%???}")
fi

if [ -n "$INVALID_CHQ_ID" ]; then
    test_endpoint "Invalid Clear" "POST" "/api/v1/cheques/$INVALID_CHQ_ID/clear" "" "400"
fi

echo ""
echo "=== 7.4 Block Already Blocked Customer ==="
test_endpoint "Block Again" "POST" "/api/v1/customers/$CUSTOMER_ID/block" "" "200"
test_endpoint "Block Blocked" "POST" "/api/v1/customers/$CUSTOMER_ID/block" "" "400"

echo ""
echo "=========================================="
echo -e "Results: ${GREEN}$PASS passed${NC}, ${RED}$FAIL failed${NC}"
echo "=========================================="

exit $FAIL
