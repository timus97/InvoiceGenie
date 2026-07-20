# InvoiceGenie — Multi-Tenant AR Backend + Web GUI

Production-grade **Accounts Receivable** stack: Java 17, Quarkus, DDD, Hexagonal Architecture, strict multi-tenant isolation — plus a **Next.js** console under `web/`.

Extensible for future AP and GL modules.

---

## Prerequisites

| Tool | Version | Required? | Purpose |
|------|---------|-----------|---------|
| **JDK** | 17+ (17 recommended) | Yes | Compile & run Quarkus |
| **Maven** | 3.9+ | Yes | Multi-module build |
| **Node.js** | 20+ | Yes (GUI) | Next.js web console in `web/` |
| **Git** | Any recent | Yes | Source control |
| **Docker Desktop** (or Postgres 15+) | Recent | Optional | Postgres / full stack |
| **curl** or **Postman** | — | Optional | API smoke tests |
| **Kafka** | — | No (today) | Messaging is stubbed via outbox |

**Check before first run:**

```bash
java -version   # must show 17+
mvn -version    # must use the same JDK (JAVA_HOME)
```

**Windows notes:**

- Always quote `-D` properties so PowerShell does not parse them:
  ```powershell
  mvn -pl ar-bootstrap "-Dquarkus.profile=dev" quarkus:dev
  ```
- Point `JAVA_HOME` at a JDK 17+ install. If `java -version` and `mvn -version` disagree, Maven will fail oddly.
- This repo includes a `.mvn/` marker so Maven resolves the project root correctly on Windows.

---

## Quick Start

### Fastest path — H2 in-memory (no Postgres)

Profile **`dev`** uses **H2** (`jdbc:h2:mem:testdb`). Data is ephemeral. Port **8080**.

```bash
# Build
mvn clean install -DskipTests

# Run (recommended local path)
mvn -pl ar-bootstrap -Dquarkus.profile=dev -Dquarkus.kafka.devservices.enabled=false quarkus:dev
```

Or use the helper scripts:

```bash
# Linux / macOS / Git Bash
./scripts/dev-up.sh

# Windows PowerShell
./scripts/dev-up.ps1
```

**Legacy alias:** `-Dquarkus.profile=sqlite` still works (same as `dev`); it is **not** a SQLite file database.

### Postgres path (production-like)

```bash
# 1. Start Postgres
docker compose up -d postgres

# 2. Apply schema once (psql or any SQL client)
# docker exec -i <postgres-container> psql -U ar -d invoicegenie < docs/sql/001_init_ar_schema.sql

# 3. Build & run default profile
mvn clean install -DskipTests
mvn -pl ar-bootstrap -Dquarkus.kafka.devservices.enabled=false quarkus:dev
```

Default JDBC: `jdbc:postgresql://localhost:5432/invoicegenie` · user/pass `ar`/`ar`.

### Full Docker stack

```bash
docker compose up -d --build
```

App: `http://localhost:8080` · Postgres: `localhost:5432`.

### Web GUI (Next.js)

The UI lives in **`web/`** and proxies `/api/*` and `/q/*` to the Quarkus backend (default `BACKEND_URL=http://localhost:8080`), so the browser stays same-origin and does not need CORS.

```bash
# Terminal 1 — API (H2)
mvn -pl ar-bootstrap "-Dquarkus.profile=dev" "-Dquarkus.kafka.devservices.enabled=false" quarkus:dev

# Terminal 2 — UI
cd web
cp .env.example .env.local   # first time only
npm install
npm run dev
```

| URL | Description |
|-----|-------------|
| `http://localhost:3000` | AR console (Dashboard, Customers, Invoices, …) |
| Settings → tenant | Sets `X-Tenant-Id` (default smoke UUID below) |

Default smoke tenant: `00000000-0000-0000-0000-000000000001`

More detail: [web/README.md](web/README.md).

### Endpoints once running

| URL | Description |
|-----|-------------|
| `http://localhost:3000` | Web GUI |
| `http://localhost:8080/api/v1/invoices` | REST API |
| `http://localhost:8080/q/swagger-ui/` | Swagger UI |
| `http://localhost:8080/q/openapi` | OpenAPI JSON |
| `http://localhost:8080/q/health` | Health check |

If port 8080 is busy:

```bash
mvn -pl ar-bootstrap -Dquarkus.profile=dev -Dquarkus.http.port=8082 quarkus:dev
```

### Smoke test

```bash
curl -s http://localhost:8080/q/health

# Create a customer first, then issue an invoice with customerId (UUID)
curl -s -X POST http://localhost:8080/api/v1/customers \
  -H "Content-Type: application/json" \
  -H "X-Tenant-Id: 00000000-0000-0000-0000-000000000001" \
  -d '{"customerCode":"C1","legalName":"Acme","currency":"USD"}'

curl -s -X POST http://localhost:8080/api/v1/invoices \
  -H "Content-Type: application/json" \
  -H "X-Tenant-Id: 00000000-0000-0000-0000-000000000001" \
  -d '{"invoiceNumber":"INV-1","customerId":"<customer-uuid>","currencyCode":"USD","dueDate":"2026-12-31","lines":[{"description":"Svc","amount":100}]}'
```

## Testing

```bash
# Unit tests (all modules)
mvn test

# Coverage reports + 80% line floor (PowerShell / bash)
./scripts/coverage.ps1
./scripts/coverage.sh

# API smoke tests against a running server (dev profile)
./scripts/test-api.ps1 -BaseUrl http://localhost:8080
./scripts/test-api.sh

# Run persistence adapter tests only
mvn -pl ar-adapter-persistence test

# Run domain lifecycle tests only
mvn -pl ar-domain test
```

### Running the Application (detail)

```bash
# Build all modules
mvn clean install -DskipTests

# (Recommended before dev) Compile dependencies for hot reload
mvn -pl ar-bootstrap -am compile -DskipTests

# H2 dev profile (no Postgres) — preferred for local exploration
mvn -pl ar-bootstrap -Dquarkus.profile=dev -Dquarkus.kafka.devservices.enabled=false quarkus:dev

# Default profile (requires PostgreSQL on localhost:5432)
mvn -pl ar-bootstrap -Dquarkus.kafka.devservices.enabled=false quarkus:dev
```

### Dev profile notes (`dev` / legacy `sqlite`)

| Fact | Detail |
|------|--------|
| Database | **H2 in-memory**, not SQLite file DB |
| JDBC URL | `jdbc:h2:mem:testdb` |
| Port | **8080** (same as default; override with `-Dquarkus.http.port`) |
| Schema | Hibernate `generation: update` from entities |
| Data | Lost on process exit |
| Kafka | Not required; disable Dev Services with `-Dquarkus.kafka.devservices.enabled=false` |

### API Testing with cURL / Postman

**Headers required for all requests:**
```
Content-Type: application/json
X-Tenant-Id: <uuid>   (or set via TenantContext in real usage)
Idempotency-Key: <unique-key>  (optional, for POST)
```

**Endpoints:**

| Method | Endpoint | Description |
|--------|----------|-------------|
| POST | `/api/v1/invoices` | Create + issue invoice |
| GET | `/api/v1/invoices/{id}` | Get single invoice |
| GET | `/api/v1/invoices?limit=20&cursor=...&status=ISSUED` | List with pagination/filter |
| POST | `/api/v1/invoices/{id}/issue` | Issue (DRAFT→ISSUED) |
| POST | `/api/v1/invoices/{id}/overdue?today=2026-01-01` | Mark overdue |
| POST | `/api/v1/invoices/{id}/writeoff` body: `{"reason":"bad debt"}` | Write off |
| POST | `/api/v1/invoices/{id}/payment` body: `{"fullyPaid":true}` | Apply payment |
| PATCH | `/api/v1/invoices/{id}/due-date` body: `{"dueDate":"2026-05-01"}` | Update due date |
| DELETE | `/api/v1/invoices/{id}` | 405 (use write-off) |

### Payment Allocation

One payment can be allocated to multiple invoices. Supports FIFO auto-allocation and manual allocation.

**Endpoints:**

| Method | Endpoint | Description |
|--------|----------|-------------|
| POST | `/api/v1/payments/{id}/allocate/fifo` | Auto-allocate to oldest invoices first |
| POST | `/api/v1/payments/{id}/allocate/manual` | Manual allocation to specific invoices |
| GET | `/api/v1/payments/{id}/allocations` | Get all allocations for a payment |
| GET | `/api/v1/payments/invoices/{id}/allocations` | Get all allocations for an invoice |

**Features:**
- **FIFO Auto-Allocation**: Allocates to oldest open invoices first (by dueDate, then issueDate)
- **Manual Allocation**: Specify exact amounts per invoice
- **Idempotency**: Use `Idempotency-Key` header to prevent duplicate allocations
- **Concurrency Safe**: Optimistic locking via payment version
- **Multi-Invoice**: One payment can cover multiple invoices

**Example - Manual Allocation:**
```bash
curl -X POST http://localhost:8080/api/v1/payments/{paymentId}/allocate/manual \
  -H "Content-Type: application/json" \
  -H "X-Tenant-Id: <uuid>" \
  -H "Idempotency-Key: unique-key-123" \
  -d '{
    "allocatedBy": "user-uuid",
    "allocations": [
      {"invoiceId": "inv-1-uuid", "amount": 100.00, "notes": "Partial payment"},
      {"invoiceId": "inv-2-uuid", "amount": 50.00, "notes": "Remaining balance"}
    ]
  }'
```

**Test Scenarios:**

### Double-Entry Ledger

Built-in double-entry accounting system with validation (debits = credits).

**Endpoints:**

| Method | Endpoint | Description |
|--------|----------|-------------|
| GET | `/api/v1/ledger/accounts` | List all available accounts |
| GET | `/api/v1/ledger/balance/{account}` | Get balance for an account |
| GET | `/api/v1/ledger/transactions/{id}` | Get all entries for a transaction |
| GET | `/api/v1/ledger/reference/{type}/{id}` | Get entries for invoice/payment |

**Accounts:**

| Account | Type | Description |
|---------|------|-------------|
| AR | Asset | Accounts Receivable - money owed by customers |
| REVENUE | Revenue | Sales revenue |
| BANK | Asset | Bank account balance |
| EXPENSE | Expense | Operating expenses |

**Journal Entries (Automatic):**

| Event | Debit | Credit |
|-------|-------|--------|
| Invoice Issued | AR | Revenue |
| Payment Received | Bank | AR |
| Write-off | Expense | AR |

**Features:**
- **Double-Entry**: Every transaction has equal debits and credits
- **Validation**: Automatic balance checking
- **Audit Trail**: Full transaction history with references

**Example - Check Account Balance:**
```bash
curl -X GET http://localhost:8080/api/v1/ledger/balance/AR \
  -H "X-Tenant-Id: <uuid>"
```

### Cheque Processing

Cheque payment system with full lifecycle management and bounce handling.

**State Transitions:**
```
RECEIVED → DEPOSITED → CLEARED
                    ↘
                  BOUNCED
```

**Endpoints:**

| Method | Endpoint | Description |
|--------|----------|-------------|
| POST | `/api/v1/cheques` | Create cheque (RECEIVED state) |
| POST | `/api/v1/cheques/{id}/deposit` | Deposit cheque to bank (RECEIVED → DEPOSITED) |
| POST | `/api/v1/cheques/{id}/clear` | Clear cheque (DEPOSITED → CLEARED) |
| POST | `/api/v1/cheques/{id}/bounce` | Bounce cheque (DEPOSITED → BOUNCED) |
| GET | `/api/v1/cheques/{id}` | Get cheque details |
| GET | `/api/v1/cheques?status=DEPOSITED` | List cheques by status |

**Example - Create Cheque:**
```bash
curl -X POST http://localhost:8080/api/v1/cheques \
  -H "Content-Type: application/json" \
  -H "X-Tenant-Id: <uuid>" \
  -d '{
    "chequeNumber": "CHQ-001",
    "customerId": "<customer-uuid>",
    "amount": 1000.00,
    "currencyCode": "USD",
    "bankName": "Test Bank",
    "bankBranch": "Main Branch",
    "chequeDate": "2026-03-20",
    "notes": "Payment for invoice"
  }'
```

**Example - Bounce Cheque:**
```bash
curl -X POST http://localhost:8080/api/v1/cheques/{id}/bounce \
  -H "Content-Type: application/json" \
  -H "X-Tenant-Id: <uuid>" \
  -d '{"reason": "Insufficient funds"}'
```

**Bounce Handling:**
- Creates reverse ledger entries: Debit AR, Credit Bank
- Reopens affected invoices (PAID → ISSUED)
- Cheque enters terminal BOUNCED state

### Aging Reports & Early Payment Discount

Aging engine for AR analysis with early payment discount (2%).

**Aging Buckets:**
| Bucket | Description | Discount Eligible |
|--------|-------------|-------------------|
| 0-30 days | Current | Yes (2% discount) |
| 31-60 days | 1-2 months overdue | No |
| 61-90 days | 2-3 months overdue | No |
| 90+ days | Severely overdue | No |

**Endpoints:**

| Method | Endpoint | Description |
|--------|----------|-------------|
| GET | `/api/v1/aging` | Generate aging report |
| GET | `/api/v1/aging/buckets` | Get bucket labels |
| POST | `/api/v1/aging/discount/calculate` | Calculate 2% early payment discount |
| POST | `/api/v1/credit-notes` | Generate credit note |
| POST | `/api/v1/credit-notes/{id}/apply` | Apply credit note to payment |

**Early Payment Discount:**
- 2% discount for invoices paid within 30 days
- Generates credit note for the discount amount
- Credit note can be applied to short payments

### Data Persistence

Use Docker Compose for PostgreSQL with persistent volume:
```bash
docker-compose up -d
```

This ensures data survives container restarts.

**Test Scenarios:**

1. **Happy Path — Issue & Pay**
   ```bash
   # Create (auto-issues)
   curl -X POST http://localhost:8080/api/v1/invoices \
     -H "Content-Type: application/json" -H "X-Tenant-Id: <uuid>" \
     -d '{"invoiceNumber":"INV-1","customerRef":"C1","currencyCode":"USD","dueDate":"2026-04-30","lines":[{"description":"Svc","amount":100}]}'
   # Response: 201, {"id":"..."}
   
   # Apply full payment → status PAID
   curl -X POST http://localhost:8080/api/v1/invoices/{id}/payment \
     -H "Content-Type: application/json" -H "X-Tenant-Id: <uuid>" \
     -d '{"fullyPaid":true}'
   ```

2. **Overdue → Write-off**
   ```bash
   # Create with past due date
   curl -X POST http://localhost:8080/api/v1/invoices \
     -H "Content-Type: application/json" -H "X-Tenant-Id: <uuid>" \
     -d '{"invoiceNumber":"INV-2","customerRef":"C2","currencyCode":"USD","dueDate":"2020-01-01","lines":[{"description":"Old","amount":50}]}'
   # Already ISSUED; mark overdue
   curl -X POST "http://localhost:8080/api/v1/invoices/{id}/overdue?today=2026-01-01" \
     -H "X-Tenant-Id: <uuid>"
   # Write off
   curl -X POST http://localhost:8080/api/v1/invoices/{id}/writeoff \
     -H "Content-Type: application/json" -H "X-Tenant-Id: <uuid>" \
     -d '{"reason":"Uncollectible"}'
   # Status: WRITTEN_OFF
   ```

3. **Validation Errors**
   - Missing lines → 400 `VALIDATION_ERROR`
   - `dueDate` before `issueDate` → 400
   - Issue already ISSUED → 409 `STATE_ERROR`

4. **Pagination**
   ```bash
   curl "http://localhost:8080/api/v1/invoices?limit=5&status=ISSUED" -H "X-Tenant-Id: <uuid>"
   # Response: {items:[...], nextCursor:"...", total:5}
   # Next page: append &cursor=<nextCursor>
   ```

### Running the Test Script

```bash
# Make executable
chmod +x scripts/test-api.sh

# Run against local dev server
./scripts/test-api.sh http://localhost:8080

# With custom tenant
TENANT_ID=aaaaaaaa-bbbb-cccc-dddd-eeeeeeeeeeee ./scripts/test-api.sh
```

### Complete Test Flow (Manual)

**1. Start the Application**
```bash
# Using Docker Compose (recommended for persistence)
docker-compose up -d

# Or using H2 dev profile (no Postgres)
mvn -pl ar-bootstrap -Dquarkus.profile=dev -Dquarkus.kafka.devservices.enabled=false quarkus:dev
```

**2. Health Check**
```bash
curl -X GET http://localhost:8080/q/health \
  -H "X-Tenant-Id: 00000000-0000-0000-0000-000000000001"
```

**3. Invoice Workflow**
```bash
# Create + Issue Invoice
curl -X POST http://localhost:8080/api/v1/invoices \
  -H "Content-Type: application/json" \
  -H "X-Tenant-Id: 00000000-0000-0000-0000-000000000001" \
  -d '{"invoiceNumber":"INV-001","customerRef":"CUST-001","currencyCode":"USD","dueDate":"2026-12-31","lines":[{"description":"Consulting","amount":1000.00}]}'

# Response: {"id":"...", "status":"ISSUED", ...}
# Save the invoiceId from response

# Apply Payment
curl -X POST http://localhost:8080/api/v1/invoices/{invoiceId}/payment \
  -H "Content-Type: application/json" \
  -H "X-Tenant-Id: 00000000-0000-0000-0000-000000000001" \
  -d '{"fullyPaid":true}'
```

**4. Payment Allocation**
```bash
# FIFO Auto-Allocation
curl -X POST http://localhost:8080/api/v1/payments/{paymentId}/allocate/fifo \
  -H "Content-Type: application/json" \
  -H "X-Tenant-Id: 00000000-0000-0000-0000-000000000001" \
  -d '{"allocatedBy":"00000000-0000-0000-0000-000000000001","version":1}'

# Manual Allocation
curl -X POST http://localhost:8080/api/v1/payments/{paymentId}/allocate/manual \
  -H "Content-Type: application/json" \
  -H "X-Tenant-Id: 00000000-0000-0000-0000-000000000001" \
  -d '{"allocatedBy":"...","version":1,"allocations":[{"invoiceId":"...","amount":500.00}]}'
```

**5. Ledger Operations**
```bash
# List Accounts
curl -X GET http://localhost:8080/api/v1/ledger/accounts \
  -H "X-Tenant-Id: 00000000-0000-0000-0000-000000000001"

# Get Account Balance
curl -X GET http://localhost:8080/api/v1/ledger/balance/AR \
  -H "X-Tenant-Id: 00000000-0000-0000-0000-000000000001"
```

**6. Cheque Processing**
```bash
# Create Cheque (RECEIVED)
curl -X POST http://localhost:8080/api/v1/cheques \
  -H "Content-Type: application/json" \
  -H "X-Tenant-Id: 00000000-0000-0000-0000-000000000001" \
  -d '{"chequeNumber":"CHQ-001","customerId":"...","amount":1000.00,"currencyCode":"USD","bankName":"Test Bank","chequeDate":"2026-03-20"}'

# Deposit Cheque (RECEIVED → DEPOSITED)
curl -X POST http://localhost:8080/api/v1/cheques/{chequeId}/deposit \
  -H "X-Tenant-Id: 00000000-0000-0000-0000-000000000001"

# Clear Cheque (DEPOSITED → CLEARED)
curl -X POST http://localhost:8080/api/v1/cheques/{chequeId}/clear \
  -H "X-Tenant-Id: 00000000-0000-0000-0000-000000000001"

# Bounce Cheque (DEPOSITED → BOUNCED)
curl -X POST http://localhost:8080/api/v1/cheques/{chequeId}/bounce \
  -H "Content-Type: application/json" \
  -H "X-Tenant-Id: 00000000-0000-0000-0000-000000000001" \
  -d '{"reason":"Insufficient funds"}'
```

**7. Aging Reports**
```bash
# Generate Aging Report
curl -X GET "http://localhost:8080/api/v1/aging?asOfDate=2026-03-21" \
  -H "X-Tenant-Id: 00000000-0000-0000-0000-000000000001"

# Calculate Early Payment Discount
curl -X POST http://localhost:8080/api/v1/aging/discount/calculate \
  -H "Content-Type: application/json" \
  -H "X-Tenant-Id: 00000000-0000-0000-0000-000000000001" \
  -d '{"amount":1000.00,"currencyCode":"USD","dueDate":"2026-04-30"}'
```

**8. Credit Notes**
```bash
# Generate Credit Note (2% early payment discount)
curl -X POST http://localhost:8080/api/v1/credit-notes \
  -H "Content-Type: application/json" \
  -H "X-Tenant-Id: 00000000-0000-0000-0000-000000000001" \
  -d '{"customerId":"...","discountAmount":20.00,"currencyCode":"USD","referenceInvoiceId":"..."}'

# Apply Credit Note to Payment
curl -X POST http://localhost:8080/api/v1/credit-notes/{creditNoteId}/apply \
  -H "Content-Type: application/json" \
  -H "X-Tenant-Id: 00000000-0000-0000-0000-000000000001" \
  -d '{"paymentId":"..."}'
```

**Design Docs:**
- [docs/ONBOARDING.md](docs/ONBOARDING.md) — architecture blueprint, module map, known gaps, local runbook.
- [docs/SCHEMA.md](docs/SCHEMA.md) — full SQL schema documentation, ER diagram, design decisions.
- [docs/FEATURE_PRIORITY_BACKLOG.md](docs/FEATURE_PRIORITY_BACKLOG.md) — prioritized incomplete/missing features & refactors.
- [docs/PRODUCTION_READINESS.md](docs/PRODUCTION_READINESS.md) — production requirements + local machine verification.
- [docs/sql/001_init_ar_schema.sql](docs/sql/001_init_ar_schema.sql) — executable PostgreSQL migration.

---

## Dependency security scanning

Backend (OWASP Dependency-Check) and frontend (`npm audit`) are wired into the repo.

```bash
# Windows PowerShell
./scripts/security-scan.ps1              # fail on High/Critical (CVSS >= 7) + npm audit
./scripts/security-scan.ps1 -ReportOnly  # generate reports without failing

# Linux / macOS / Git Bash
./scripts/security-scan.sh
./scripts/security-scan.sh --report-only
```

Maven-only (aggregate report under `target/dependency-check/`):

```bash
mvn -Psecurity-scan org.owasp:dependency-check-maven:aggregate
```

Frontend-only:

```bash
cd web
npm run audit        # production deps
npm run audit:all    # all deps
npm run audit:ci     # fail on high+
```

Optional: set `NVD_API_KEY` for faster/full NVD updates with OWASP Dependency-Check.

---

## Database Schema

**Database:** PostgreSQL 15+ (H2 in-memory via `dev` profile for local)  
**Multi-tenancy:** Single DB, `tenant_id` column + RLS  
**IDs:** UUID v7 (time-ordered)  
**Currency:** ISO 4217 (3-char codes)

### Entities

| Entity | Table | Description |
|--------|-------|-------------|
| **Tenant** | `ar_tenant` | Tenant registry; owns all data; base currency; settings JSONB |
| **Customer** | `ar_customer` | AR customer master; per-tenant; currency, credit limit, payment terms |
| **Invoice** | `ar_invoice` | Invoice aggregate root; versioned; status, totals, amount_due |
| **InvoiceVersion** | `ar_invoice_version` | Full JSONB snapshot per version change; immutable audit trail |
| **InvoiceLine** | `ar_invoice_line` | Line items; quantity, unit_price, tax, discount |
| **Payment** | `ar_payment` | Payment received; method, reference, unallocated amount |
| **PaymentAllocation** | `ar_payment_allocation` | Many-to-many: payment → N invoices; supports partials/overpayments |
| **Account** | `ar_account` | Chart of accounts (AR subset); ASSET/LIABILITY/REVENUE; hierarchy |
| **LedgerEntry** | `ar_ledger_entry` | GL-style single-sided entries; debit/credit; links to invoice/payment |
| **AuditLog** | `ar_audit_log` | Unified audit trail; before/after JSONB; actor, IP, user-agent |
| **ExchangeRate** | `ar_exchange_rate` | Optional; cross-currency reporting (base currency per tenant) |
| **Outbox** | `ar_outbox` | Transactional outbox for domain events → Kafka |

### Key Design Decisions

| Decision | Choice | Rationale |
|----------|--------|-----------|
| **ID Strategy** | UUID v7 (time-ordered) | No central bottleneck; good for partitioning; sortable |
| **Tenant Storage** | Single DB, `tenant_id UUID NOT NULL` on every table | Simpler ops, one schema, easier backups |
| **Tenant Isolation** | App-level filter + RLS policy | Defence in depth; even buggy SQL can't leak |
| **Invoice Versioning** | Separate `ar_invoice_version` (full JSONB snapshot) | Audit trail; no mutation loss |
| **Payment Allocation** | `ar_payment_allocation` (many-to-many) | Partial payments, overpayments, multi-invoice allocation |
| **Ledger** | `ar_ledger_entry` with `account_id` | GL-ready; AR publishes events, GL subscribes (future) |
| **Audit Log** | `ar_audit_log` with before/after JSONB | Unified; flexible; queryable |
| **Money Precision** | `NUMERIC(19,2)` | Standard 2-decimal; ISO 4217 currencies |
| **Indexes** | All lead with `(tenant_id, ...)` | Leverages RLS; fast tenant-scoped queries |

### Invoice Status Flow

```
DRAFT → ISSUED → PARTIALLY_PAID → PAID
                ↓
              OVERDUE → WRITTEN_OFF
```

### Invoice Lifecycle Engine

- Lifecycle enforced in domain (`InvoiceLifecycleEngine`).
- Terminal states: `PAID`, `WRITTEN_OFF`.
- Overdue only when `today > dueDate`, write-off requires reason.

### Payment Allocation Rules

- One payment can allocate to N invoices
- One invoice can receive allocations from N payments
- `amount_unallocated = payment.amount - SUM(allocation.amount)`
- Allocation currency must match both payment and invoice
- Amount must be > 0
- Application layer enforces: total allocations ≤ payment amount

### Multi-Currency

- Invoice has its own `currency` (ISO 4217)
- Payment has its own `currency`
- Tenant has `base_currency` for reporting
- Optional `ar_exchange_rate` for cross-currency conversions
- `amount_base` on ledger for base-currency reporting

---

## Architecture

```
┌─────────────────────────────────────────────────────────────────────────┐
│                          INBOUND (Driving)                              │
│  REST API (JAX-RS) / gRPC / Kafka Consumer                              │
└─────────────────────────────────┬───────────────────────────────────────┘
                                  │
                                  ▼
┌─────────────────────────────────────────────────────────────────────────┐
│                    APPLICATION LAYER (Use Cases)                        │
│  IssueInvoice, RecordPayment, ApplyAllocation, CancelInvoice, etc.      │
└─────────────────────────────────┬───────────────────────────────────────┘
                                  │
                                  ▼
┌─────────────────────────────────────────────────────────────────────────┐
│                     DOMAIN LAYER (AR Bounded Context)                   │
│  Aggregates: Invoice, Payment, Customer                                 │
│  Entities:   InvoiceLine, PaymentAllocation                             │
│  Events:     InvoiceIssued, PaymentRecorded, PaymentAllocated           │
└─────────────────────────────────┬───────────────────────────────────────┘
                                  │
                                  ▼
┌─────────────────────────────────────────────────────────────────────────┐
│                     OUTBOUND PORTS (Interfaces)                         │
│  InvoiceRepository, PaymentRepository, CustomerRepository,              │
│  EventPublisher, TenantResolver, IdGenerator                            │
└─────────────────────────────────┬───────────────────────────────────────┘
                                  │
                                  ▼
┌─────────────────────────────────────────────────────────────────────────┐
│                     OUTBOUND ADAPTERS (Infrastructure)                  │
│  PostgreSQL (JPA), Kafka, TenantFilter, UUID v7 Generator               │
└─────────────────────────────────────────────────────────────────────────┘
```

**Modules:**
- `shared-kernel` — TenantId, Money, EntityId, DomainEvent (cross-cutting)
- `ar-domain` — Pure domain (zero infra deps)
- `ar-application` — Use cases, ports (no infra)
- `ar-adapter-api` — REST/gRPC endpoints
- `ar-adapter-persistence` — JPA, DB adapters
- `ar-adapter-messaging` — Kafka event publisher
- `ar-bootstrap` — Quarkus app wiring

---

## Tenant Isolation

**Database Layer:**
- Every table: `tenant_id UUID NOT NULL`
- Composite PK: `(tenant_id, id)` or unique `(tenant_id, business_key)`
- RLS enabled on all tables; policy: `tenant_id = current_setting('app.current_tenant_id')::uuid`

**Application Layer:**
- `TenantFilter` resolves `TenantId` from JWT (`tenant_id` or `sub` claim)
- Sets `TenantContext.setCurrentTenant(tenantId)` before any use case
- All repository methods require `TenantId` parameter explicitly
- No `findById(id)` — always `findByTenantAndId(tenantId, id)`

**Outbox Events:**
- Every Kafka message includes `tenant_id` header
- Subscribers filter by tenant

---

## Scale Notes

- **Pagination:** Cursor-based `(tenant_id, created_at, id)`; max 1000 per page
- **Indexes:** All tenant-scoped; avoid full table scans
- **Outbox:** Transactional outbox → worker → Kafka (no dual-write)
- **Caching:** Tenant metadata only; no tenant-scoped entity caching
- **Partitioning:** Optional by tenant_id for mega-tenants (>10M rows)

---

## Future Extensions

| Module | Responsibility |
|--------|----------------|
| **AP (Payables)** | Vendor invoices, bill payments — separate bounded context |
| **GL (General Ledger)** | Consumes AR/AP events; maintains journal; trial balance |
| **Dunning** | Overdue reminders, escalation workflows |
| **Reporting** | Aging, cash forecast, revenue recognition |

---

## License

Proprietary — InvoiceGenie project.
