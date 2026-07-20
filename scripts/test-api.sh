#!/usr/bin/env bash
# InvoiceGenie API smoke suite (Linux / GitHub Actions)
# Aligns with scripts/test-api.ps1 for prod Docker (API key + demo tenant).
#
# Usage:
#   ./scripts/test-api.sh [base_url]
#   TENANT_ID=... INVOICEGENIE_API_KEY=... ./scripts/test-api.sh http://localhost:8080

set -uo pipefail

BASE_URL="${1:-http://localhost:8080}"
TENANT_ID="${TENANT_ID:-00000000-0000-0000-0000-000000000001}"
API_KEY="${INVOICEGENIE_API_KEY:-${NEXT_PUBLIC_API_KEY:-}}"

PASS=0
FAIL=0

RED='\033[0;31m'
GREEN='\033[0;32m'
CYAN='\033[0;36m'
NC='\033[0m'

echo "=========================================="
echo "InvoiceGenie API Test Suite (bash)"
echo "Base URL: $BASE_URL"
echo "Tenant:   $TENANT_ID"
echo "API Key:  $([ -n "$API_KEY" ] && echo '(set)' || echo '(none — ok for dev profile)')"
echo "=========================================="

json_get_id() {
  local json="$1"
  if command -v python3 >/dev/null 2>&1; then
    python3 -c "import sys,json; d=json.load(sys.stdin); print(d.get('id') or '')" <<<"$json" 2>/dev/null || true
  elif command -v jq >/dev/null 2>&1; then
    jq -r '.id // empty' <<<"$json" 2>/dev/null || true
  else
    sed -n 's/.*"id"[[:space:]]*:[[:space:]]*"\([^"]*\)".*/\1/p' <<<"$json" | head -1
  fi
}

api() {
  local name="$1" method="$2" path="$3" expected="$4" body="${5:-}" idem="${6:-}"
  local args=(-sS -w "\n%{http_code}" -X "$method"
    -H "Content-Type: application/json"
    -H "X-Tenant-Id: $TENANT_ID")
  if [ -n "$API_KEY" ]; then
    args+=(-H "X-API-Key: $API_KEY")
  fi
  if [ -n "$idem" ]; then
    args+=(-H "Idempotency-Key: $idem")
  fi
  if [ -n "$body" ]; then
    args+=(-d "$body")
  fi

  echo -n "[$name] $method $path ... "
  local raw code content
  raw=$(curl "${args[@]}" "$BASE_URL$path" 2>/dev/null || echo -e "\n000")
  code=$(printf '%s' "$raw" | tail -n1)
  content=$(printf '%s' "$raw" | sed '$d')

  if [ "$code" = "$expected" ]; then
    echo -e "${GREEN}PASS${NC} ($code)"
    PASS=$((PASS + 1))
    printf '%s' "$content"
    return 0
  fi
  echo -e "${RED}FAIL${NC} (expected $expected, got $code)"
  if [ -n "$content" ]; then
    echo "$content"
  fi
  FAIL=$((FAIL + 1))
  printf '%s' "$content"
  return 1
}

echo -n "Waiting for server..."
ready=0
for _ in $(seq 1 90); do
  if curl -sf -o /dev/null "$BASE_URL/q/health"; then
    ready=1
    break
  fi
  echo -n "."
  sleep 2
done
if [ "$ready" -ne 1 ]; then
  echo " TIMEOUT"
  exit 1
fi
echo " ready"

TS=$(date +%s)

echo ""
echo -e "${CYAN}=== SECTION 1: CUSTOMER ===${NC}"
CUST_JSON=$(api "Create Customer" POST /api/v1/customers 201 \
  "{\"customerCode\":\"CUST-$TS\",\"legalName\":\"Test Customer Inc.\",\"currency\":\"USD\"}" || true)
CUSTOMER_ID=$(json_get_id "$CUST_JSON")
echo "  Customer ID: $CUSTOMER_ID"
if [ -n "$CUSTOMER_ID" ]; then
  api "Get Customer" GET "/api/v1/customers/$CUSTOMER_ID" 200 >/dev/null || true
fi
api "List Customers" GET /api/v1/customers 200 >/dev/null || true

echo ""
echo -e "${CYAN}=== SECTION 2: INVOICE ===${NC}"
INVOICE_ID=""
if [ -n "$CUSTOMER_ID" ]; then
  INV_JSON=$(api "Create Invoice" POST /api/v1/invoices 201 \
    "{\"invoiceNumber\":\"INV-TEST-$TS\",\"customerId\":\"$CUSTOMER_ID\",\"customerRef\":\"API-Test\",\"currencyCode\":\"USD\",\"dueDate\":\"2026-12-31\",\"lines\":[{\"description\":\"Consulting\",\"amount\":5000.00},{\"description\":\"Expenses\",\"amount\":500.00}]}" || true)
  INVOICE_ID=$(json_get_id "$INV_JSON")
  echo "  Invoice ID: $INVOICE_ID"
  if [ -n "$INVOICE_ID" ]; then
    api "Get Invoice" GET "/api/v1/invoices/$INVOICE_ID" 200 >/dev/null || true
  fi
fi
api "List Invoices" GET "/api/v1/invoices?limit=10" 200 >/dev/null || true

echo ""
echo -e "${CYAN}=== SECTION 3: PAYMENTS ===${NC}"
PAYMENT_ID=""
if [ -n "$CUSTOMER_ID" ]; then
  PAY_DATE=$(date -u +%Y-%m-%d)
  PAY_JSON=$(api "Record Payment" POST /api/v1/payments 201 \
    "{\"paymentNumber\":\"PAY-$TS\",\"customerId\":\"$CUSTOMER_ID\",\"amount\":1000.00,\"currencyCode\":\"USD\",\"paymentDate\":\"$PAY_DATE\",\"method\":\"BANK_TRANSFER\",\"reference\":\"REF-$TS\",\"notes\":\"API test payment\"}" || true)
  PAYMENT_ID=$(json_get_id "$PAY_JSON")
  echo "  Payment ID: $PAYMENT_ID"
  if [ -n "$PAYMENT_ID" ] && [ -n "$INVOICE_ID" ]; then
    api "Manual Allocate" POST "/api/v1/payments/$PAYMENT_ID/allocate/manual" 200 \
      "{\"allocatedBy\":\"$TENANT_ID\",\"allocations\":[{\"invoiceId\":\"$INVOICE_ID\",\"amount\":500.00,\"notes\":\"partial\"}]}" >/dev/null || true
    api "Get Allocations" GET "/api/v1/payments/$PAYMENT_ID/allocations" 200 >/dev/null || true
  fi
fi

echo ""
echo -e "${CYAN}=== SECTION 4: AGING ===${NC}"
api "Aging Report" GET /api/v1/aging 200 >/dev/null || true

echo ""
echo -e "${CYAN}=== SECTION 5: CHEQUES ===${NC}"
if [ -n "$CUSTOMER_ID" ]; then
  CHQ_JSON=$(api "Create Cheque" POST /api/v1/cheques 201 \
    "{\"chequeNumber\":\"CHQ-$TS\",\"customerId\":\"$CUSTOMER_ID\",\"amount\":1000.00,\"currencyCode\":\"USD\",\"bankName\":\"Test Bank\",\"bankBranch\":\"Main\",\"chequeDate\":\"2026-03-20\",\"notes\":\"test\"}" || true)
  CHEQUE_ID=$(json_get_id "$CHQ_JSON")
  if [ -n "$CHEQUE_ID" ]; then
    api "List Cheques (unfiltered)" GET /api/v1/cheques 200 >/dev/null || true
    api "Deposit Cheque" POST "/api/v1/cheques/$CHEQUE_ID/deposit" 200 >/dev/null || true
    api "Clear Cheque" POST "/api/v1/cheques/$CHEQUE_ID/clear" 200 >/dev/null || true
  fi
fi

echo ""
echo -e "${CYAN}=== SECTION 6: CREDIT NOTES ===${NC}"
if [ -n "$CUSTOMER_ID" ] && [ -n "$INVOICE_ID" ]; then
  api "Create Credit Note" POST /api/v1/credit-notes 201 \
    "{\"customerId\":\"$CUSTOMER_ID\",\"discountAmount\":100.00,\"currencyCode\":\"USD\",\"referenceInvoiceId\":\"$INVOICE_ID\"}" >/dev/null || true
fi
api "List Credit Notes" GET /api/v1/credit-notes 200 >/dev/null || true

echo ""
echo -e "${CYAN}=== SECTION 7: OUTBOX ===${NC}"
api "Outbox Stats" GET /api/v1/outbox/stats 200 >/dev/null || true

echo ""
echo -e "${CYAN}=== SECTION 8: LEDGER / DRAFT / IDEMPOTENCY ===${NC}"
if [ -n "$INVOICE_ID" ]; then
  api "Ledger Validate (invoice)" POST /api/v1/ledger/validate 200 \
    "{\"referenceType\":\"INVOICE\",\"referenceId\":\"$INVOICE_ID\"}" >/dev/null || true
fi
if [ -n "$CUSTOMER_ID" ]; then
  DRAFT_JSON=$(api "Create DRAFT Invoice" POST /api/v1/invoices 201 \
    "{\"invoiceNumber\":\"INV-DRAFT-$TS\",\"customerId\":\"$CUSTOMER_ID\",\"customerRef\":\"Draft-Test\",\"currencyCode\":\"USD\",\"dueDate\":\"2026-12-31\",\"issueImmediately\":false,\"lines\":[{\"description\":\"Draft line\",\"amount\":250.00}]}" || true)
  DRAFT_ID=$(json_get_id "$DRAFT_JSON")
  if [ -n "$DRAFT_ID" ]; then
    GET_DRAFT=$(api "Get DRAFT Invoice" GET "/api/v1/invoices/$DRAFT_ID" 200 || true)
    if echo "$GET_DRAFT" | grep -q '"status"[[:space:]]*:[[:space:]]*"DRAFT"'; then
      echo -e "[Assert DRAFT status] ${GREEN}PASS${NC}"
      PASS=$((PASS + 1))
    else
      echo -e "[Assert DRAFT status] ${RED}FAIL${NC}"
      FAIL=$((FAIL + 1))
    fi
    api "Ledger Validate (draft=no rows)" POST /api/v1/ledger/validate 404 \
      "{\"referenceType\":\"INVOICE\",\"referenceId\":\"$DRAFT_ID\"}" >/dev/null || true
    api "Issue DRAFT Invoice" POST "/api/v1/invoices/$DRAFT_ID/issue" 200 >/dev/null || true
    api "Ledger Validate (after issue)" POST /api/v1/ledger/validate 200 \
      "{\"referenceType\":\"INVOICE\",\"referenceId\":\"$DRAFT_ID\"}" >/dev/null || true
  fi

  IDEM_KEY="smoke-pay-$TS"
  PAY_DATE=$(date -u +%Y-%m-%d)
  IDEM_BODY="{\"paymentNumber\":\"PAY-IDEM-$TS\",\"customerId\":\"$CUSTOMER_ID\",\"amount\":111.00,\"currencyCode\":\"USD\",\"paymentDate\":\"$PAY_DATE\",\"method\":\"BANK_TRANSFER\",\"reference\":\"IDEM-$TS\",\"notes\":\"idempotency smoke\"}"
  P1=$(api "Payment Idempotent (1st)" POST /api/v1/payments 201 "$IDEM_BODY" "$IDEM_KEY" || true)
  P2=$(api "Payment Idempotent (2nd)" POST /api/v1/payments 201 "$IDEM_BODY" "$IDEM_KEY" || true)
  ID1=$(json_get_id "$P1")
  ID2=$(json_get_id "$P2")
  if [ -n "$ID1" ] && [ "$ID1" = "$ID2" ]; then
    echo -e "[Assert same payment id] ${GREEN}PASS${NC} ($ID1)"
    PASS=$((PASS + 1))
  else
    echo -e "[Assert same payment id] ${RED}FAIL${NC} ($ID1 vs $ID2)"
    FAIL=$((FAIL + 1))
  fi
fi

echo ""
echo "=========================================="
echo "PASS: $PASS  FAIL: $FAIL"
echo "=========================================="
if [ "$FAIL" -gt 0 ]; then
  exit 1
fi
exit 0