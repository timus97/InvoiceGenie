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
  ↓         ↓           ↓              ↓
  └───── CANCELLED ←─────┘              ↓
            ↓                           ↓
        VOID (hard delete not allowed)
```

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
