# InvoiceGenie — Multi-Tenant AR Backend

Production-grade **Accounts Receivable** backend: Java 17, Quarkus, DDD, Hexagonal Architecture, strict multi-tenant isolation. Designed for millions of invoices.

No UI in this repo; backend only. Extensible for future AP and GL modules.

---

## Quick Start

```bash
# Build
mvn clean install

# Run (requires PostgreSQL)
mvn -pl ar-bootstrap quarkus:dev

# Run with SQLite (dev fallback, no Postgres)
mvn -pl ar-bootstrap -Dquarkus.profile=sqlite quarkus:dev
```

## Testing

```bash
# Run all tests
mvn test

# Run persistence adapter tests only
mvn -pl ar-adapter-persistence test

# Run domain lifecycle tests only
mvn -pl ar-domain test
```

### Running the Application

```bash
# Build all modules
mvn clean install -DskipTests

# Start dev server (requires PostgreSQL)
mvn -pl ar-bootstrap quarkus:dev

# Start with SQLite dev profile (no Postgres)
mvn -pl ar-bootstrap -Dquarkus.profile=sqlite quarkus:dev

# Once running, access:
# API: http://localhost:8080/api/v1/invoices
# Swagger UI: http://localhost:8080/q/swagger-ui/
# OpenAPI JSON: http://localhost:8080/q/openapi
```

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

**Design Docs:**
- [docs/AR_BACKEND_DESIGN.md](docs/AR_BACKEND_DESIGN.md) — architecture, bounded contexts, package structure, tenant isolation, scale notes.
- [docs/SCHEMA.md](docs/SCHEMA.md) — full SQL schema documentation, ER diagram, design decisions.
- [docs/sql/001_init_ar_schema.sql](docs/sql/001_init_ar_schema.sql) — executable PostgreSQL migration.

---

## Database Schema

**Database:** PostgreSQL 15+ (SQLite fallback for dev)  
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
