# Production-Grade Multi-Tenant AR Backend — Design Document

**Stack:** Java 17, Quarkus  
**Scale:** Millions of invoices per tenant  
**Principles:** DDD, Hexagonal Architecture, strict tenant isolation

---

## 1. Invoice Lifecycle Engine

The invoice lifecycle is enforced in the domain via a dedicated `InvoiceLifecycleEngine` with explicit allowed transitions.
States: `DRAFT`, `ISSUED`, `PARTIALLY_PAID`, `PAID`, `OVERDUE`, `WRITTEN_OFF`.

**Rules:**
- `DRAFT → ISSUED`
- `ISSUED → PARTIALLY_PAID | PAID | OVERDUE`
- `PARTIALLY_PAID → PAID | OVERDUE`
- `OVERDUE → PARTIALLY_PAID | PAID | WRITTEN_OFF`
- `PAID`, `WRITTEN_OFF` are terminal

**Edge Cases Handled:**
- No lines: cannot issue
- `dueDate < issueDate` rejected
- `markOverdue(today)` requires `today > dueDate`
- `writeOff(reason)` requires non-blank reason and OVERDUE status
- `writtenOffAt` set only for `WRITTEN_OFF`

---

## 2. High-Level Architecture (Textual Diagram)

---

## 1. High-Level Architecture (Textual Diagram)

```
┌─────────────────────────────────────────────────────────────────────────────────────────┐
│                              INBOUND (Driving / Primary)                                  │
├─────────────────────────────────────────────────────────────────────────────────────────┤
│                                                                                           │
│   REST API (JAX-RS)          gRPC (optional)           Message Consumer (Kafka/AMQP)     │
│   ┌──────────────┐           ┌──────────────┐           ┌─────────────────────────────┐  │
│   │ InvoiceResource│           │ ArServiceStub │           │ InvoiceCreatedHandler       │  │
│   │ PaymentResource│          │ PaymentStub   │           │ PaymentAppliedHandler       │  │
│   │ CustomerResource│         └──────┬───────┘           └──────────────┬──────────────┘  │
│   └──────┬───────┘                   │                                  │                 │
│          │                           │                                  │                 │
│          └───────────────────────────┼──────────────────────────────────┘                 │
│                                      │                                                    │
│                                      ▼                                                    │
│   ┌─────────────────────────────────────────────────────────────────────────────────┐   │
│   │                    APPLICATION LAYER (Use Cases / Application Services)            │   │
│   │  ┌─────────────────┐  ┌─────────────────┐  ┌─────────────────┐                    │   │
│   │  │ IssueInvoiceUC  │  │ RecordPaymentUC │  │ ApplyAllocationUC│  ...               │   │
│   │  └────────┬────────┘  └────────┬────────┘  └────────┬────────┘                    │   │
│   └───────────┼────────────────────┼────────────────────┼─────────────────────────────┘   │
│               │                    │                    │                                  │
│               ▼                    ▼                    ▼                                  │
│   ┌─────────────────────────────────────────────────────────────────────────────────┐   │
│   │                         DOMAIN LAYER (Bounded Context: AR)                        │   │
│   │  ┌─────────────┐  ┌─────────────┐  ┌─────────────┐  ┌─────────────┐                │   │
│   │  │   Invoice   │  │   Payment   │  │  Allocation │  │   Customer  │  (Aggregates)  │   │
│   │  │  (Aggregate)│  │  (Aggregate)│  │  (Entity)   │  │  (Aggregate)│                │   │
│   │  └─────────────┘  └─────────────┘  └─────────────┘  └─────────────┘                │   │
│   │  Domain Events: InvoiceIssued, PaymentRecorded, PaymentAllocated, Overdue          │   │
│   └─────────────────────────────────────────────────────────────────────────────────┘   │
│                                      │                                                    │
│                                      ▼                                                    │
│   ┌─────────────────────────────────────────────────────────────────────────────────┐   │
│   │                         PORTS (Interfaces — Outbound)                             │   │
│   │  InvoiceRepository   PaymentRepository   CustomerRepository   EventPublisher     │   │
│   │  TenantResolver      IdGenerator         Clock                                    │   │
│   └─────────────────────────────────────────────────────────────────────────────────┘   │
└─────────────────────────────────────────────────────────────────────────────────────────┘
                                      │
                                      │ Adapters implement Ports
                                      ▼
┌─────────────────────────────────────────────────────────────────────────────────────────┐
│                              OUTBOUND (Driven / Secondary)                               │
├─────────────────────────────────────────────────────────────────────────────────────────┤
│                                                                                           │
│   ┌─────────────────────────────┐  ┌─────────────────────────────┐                       │
│   │  PostgreSQL Adapter         │  │  Kafka Event Publisher       │                       │
│   │  • TenantId on every table  │  │  • Topic per event type      │                       │
│   │  • RLS or schema-per-tenant │  │  • Tenant in message header  │                       │
│   │  • Connection pooling       │  │                              │                       │
│   └─────────────────────────────┘  └─────────────────────────────┘                       │
│                                                                                           │
│   ┌─────────────────────────────┐  ┌─────────────────────────────┐                       │
│   │  TenantResolver (JWT/Header) │  │  UUID IdGenerator            │                       │
│   │  • Sub or tenant_id claim   │  │  (or DB sequence per tenant) │                       │
│   └─────────────────────────────┘  └─────────────────────────────┘                       │
│                                                                                           │
└─────────────────────────────────────────────────────────────────────────────────────────┘

                    ┌─────────────────────────────────────┐
                    │  SHARED KERNEL (cross-cutting)       │
                    │  TenantId, Money, Id types, Events   │
                    │  Used by AR, future AP, GL           │
                    └─────────────────────────────────────┘
```

---

## 2. Module Breakdown (Bounded Contexts)

| Bounded Context | Responsibility | Key Aggregates | Integration |
|-----------------|---------------|----------------|-------------|
| **AR (Accounts Receivable)** | Invoices, payments, allocations, aging, dunning | Invoice, Payment, Customer | Publishes InvoiceIssued, PaymentRecorded; consumed by GL |
| **Shared Kernel** | TenantId, Money, common value objects, base events | — | Imported by all contexts |
| **AP (future)** | Vendor invoices, payables | — | Consumes shared kernel; separate schema/module |
| **GL (future)** | Ledger entries, chart of accounts | — | Subscribes to AR/AP events; writes journal entries |

**Deployment:** Each bounded context can be a Quarkus application (microservice) or a packaged module within a single deployable. For millions of invoices, prefer **modular monolith first** (single deployable, clear module boundaries) with option to split AR/AP/GL later.

---

## 3. Package Structure

```
invoice-genie/
├── pom.xml
├── docs/
│   └── AR_BACKEND_DESIGN.md
│
├── shared-kernel/                          # Shared across AR, AP, GL
│   └── src/main/java/
│       └── com/invoicegenie/shared/
│           ├── domain/
│           │   ├── TenantId.java           # Value object, never null in AR
│           │   ├── Money.java
│           │   ├── EntityId.java
│           │   └── DomainEvent.java
│           └── tenant/
│               └── TenantContext.java      # ThreadLocal / request-scoped
│
├── ar-domain/                              # AR Bounded Context — pure domain
│   └── src/main/java/
│       └── com/invoicegenie/ar/domain/
│           ├── model/
│           │   ├── invoice/
│           │   │   ├── Invoice.java        # Aggregate root
│           │   │   ├── InvoiceLine.java
│           │   │   ├── InvoiceStatus.java
│           │   │   └── InvoiceRepository.java   # Port (interface)
│           │   ├── payment/
│           │   │   ├── Payment.java
│           │   │   ├── PaymentRepository.java
│           │   │   └── Allocation.java
│           │   └── customer/
│           │       ├── Customer.java
│           │       └── CustomerRepository.java
│           ├── event/
│           │   ├── InvoiceIssued.java
│           │   ├── PaymentRecorded.java
│           │   └── PaymentAllocated.java
│           └── service/
│               └── AllocationDomainService.java   # Cross-aggregate logic
│
├── ar-application/                         # Use cases, no infra
│   └── src/main/java/
│       └── com/invoicegenie/ar/application/
│           ├── port/
│           │   ├── inbound/
│           │   │   ├── IssueInvoiceUseCase.java
│           │   │   ├── RecordPaymentUseCase.java
│           │   │   └── ApplyAllocationUseCase.java
│           │   └── outbound/
│           │       ├── InvoiceRepository.java     # Re-export or extend domain port
│           │       ├── PaymentRepository.java
│           │       ├── EventPublisher.java
│           │       ├── TenantResolver.java
│           │       └── IdGenerator.java
│           └── service/
│               ├── IssueInvoiceService.java
│               ├── RecordPaymentService.java
│               └── ApplyAllocationService.java
│
├── ar-adapter-api/                         # REST / gRPC — driving adapters
│   └── src/main/java/
│       └── com/invoicegenie/ar/adapter/api/
│           ├── rest/
│           │   ├── InvoiceResource.java
│           │   ├── PaymentResource.java
│           │   ├── CustomerResource.java
│           │   └── dto/                     # API DTOs only
│           └── filter/
│               └── TenantFilter.java        # Resolve tenant, set TenantContext
│
├── ar-adapter-persistence/                 # Driven adapter — DB
│   └── src/main/java/
│       └── com/invoicegenie/ar/adapter/persistence/
│           ├── entity/                     # JPA entities (optional; or use domain mapping)
│           ├── repository/
│           │   ├── InvoiceRepositoryAdapter.java
│           │   └── PaymentRepositoryAdapter.java
│           └── mapper/
│               └── InvoicePersistenceMapper.java
│
├── ar-adapter-messaging/                  # Outbound events
│   └── src/main/java/
│       └── com/invoicegenie/ar/adapter/messaging/
│           └── KafkaEventPublisher.java
│
└── ar-bootstrap/                          # Quarkus app: wires adapters to ports
    └── src/main/java/
        └── com/invoicegenie/ar/
            └── ArApplication.java
    └── src/main/resources/
        └── application.yml
```

**Rules:**
- **ar-domain** has zero dependencies on Quarkus, JPA, or HTTP. Only `shared-kernel`.
- **ar-application** depends on `ar-domain` and defines ports; no adapter imports.
- **ar-adapter-*** implement ports and depend on `ar-application` (or domain) and infra (JPA, Kafka, etc.).
- **ar-bootstrap** pulls in all adapters and configures CDI.

---

## 4. Key Design Decisions and Tradeoffs

| Decision | Choice | Tradeoff |
|----------|--------|----------|
| **Tenant storage model** | **Single DB, tenant column (tenant_id)** on every table + RLS or application-level filtering | **Pro:** Simpler ops, one schema to migrate, easier backups. **Con:** One noisy tenant can affect others; mitigate with connection limits and RLS. **Alternative:** Schema-per-tenant scales isolation but complicates migrations and connection pooling (N schemas). **Rejected for v1:** DB-per-tenant (operational cost at scale). |
| **Tenant resolution** | **JWT `tenant_id` (or `sub` mapped to tenant) in filter/interceptor**; set before any use case runs | **Pro:** Consistent per request; no tenant in URL. **Con:** Every request must carry a valid token. |
| **Repository contract** | **All find/load methods require TenantId**; passed from application layer, never read from thread inside repository | **Pro:** Explicit; no accidental cross-tenant reads. **Con:** Boilerplate; mitigate with a small base type or wrapper. |
| **IDs** | **UUID v7 (time-ordered)** per entity; optional tenant-scoped sequence for human-readable numbers | **Pro:** No central bottleneck; good for distributed and partitioning. **Con:** Larger than bigint; index slightly heavier. For “millions per tenant,” UUID v7 is acceptable. |
| **Write path** | **One transaction per use case**; domain events published after commit (outbox or transactional listener) | **Pro:** Consistency; no event published if transaction rolls back. **Con:** Need outbox or PostCommit listener to avoid dual-write failures. |
| **Read path (list/search)** | **CQRS-style: repository returns domain objects; optional read model (e.g. dedicated tables or views) for complex queries** | **Pro:** Keeps write model simple; read model can be denormalized and partitioned by tenant. **Con:** Eventually consistent if read model is async; for AR, often acceptable for reporting. |
| **Extensibility for AP/GL** | **Shared kernel (TenantId, Money, events); AR publishes events; GL subscribes and maintains ledger** | **Pro:** AR stays focused; GL can evolve independently. **Con:** Event schema versioning and compatibility required. |

---

## 5. Tenant Isolation: DB and Application Layer

### 5.1 Database Layer

**1. Mandatory tenant column**
- Every table: `tenant_id UUID NOT NULL` (or `BIGINT` if tenants are numeric).
- Composite primary key `(tenant_id, id)` or unique index `(tenant_id, business_key)`.
- All queries (including raw SQL and criteria) **must** include `tenant_id = ?`.

**2. Row-Level Security (RLS) — recommended**
- Enable RLS on all tenant tables.
- Policy: `tenant_id = current_setting('app.current_tenant_id')::uuid`.
- Application sets `app.current_tenant_id` at the start of each connection use (e.g. when obtaining from pool).  
**Pro:** Defence in depth; even buggy SQL cannot leak data. **Con:** Must set session variable per request; connection pool must not reuse without resetting.

**3. Indexing for scale**
- All tenant-scoped queries: `(tenant_id, ...)` leading in indexes (e.g. `(tenant_id, created_at DESC)`, `(tenant_id, status, due_date)`).
- Enables partition-by-tenant later if a single tenant grows to tens of millions of rows.

**4. Optional: Partitioning**
- Table partitioning by `tenant_id` (hash or list) for very large tenants. Start without; introduce when a single tenant’s row count justifies it.

### 5.2 Application Layer

**1. Tenant resolution (single place)**
- Filter or interceptor (e.g. `TenantFilter`) runs first: resolve `TenantId` from JWT (or header), validate it (e.g. against tenant registry/cache), set `TenantContext.setCurrentTenant(tenantId)`.
- If missing or invalid → 401/403; no use case runs.

**2. No tenant in domain API**
- Domain entities and repositories do **not** accept or store `TenantId` in the aggregate root identity; it’s contextual. Repositories **do** receive `TenantId` as a separate parameter on every method (e.g. `findByTenantAndId(TenantId tenantId, InvoiceId id)`).

**3. Application layer enforces**
- Every use case gets `TenantId` from `TenantContext` (or from authenticated context) and passes it to repositories. No repository method is called without tenant.
- Code review rule: no `findById(id)` alone; always `findByTenantAndId(tenantId, id)`.

**4. Outbound events**
- Every published message includes `tenant_id` in header (or payload). Subscribers (e.g. GL) filter by tenant and never process without tenant.

**5. Validation**
- On create/update, reject if any referenced entity (e.g. customer, invoice) does not belong to the current tenant. Enforce in application service or domain service.

**Example: RLS policy (PostgreSQL)**

```sql
ALTER TABLE ar_invoice ENABLE ROW LEVEL SECURITY;

CREATE POLICY tenant_isolation ON ar_invoice
  USING (tenant_id = current_setting('app.current_tenant_id', true)::uuid);

-- Application sets app.current_tenant_id at start of each request when obtaining a connection
```

---

## 6. Scale: Millions of Invoices

- **Pagination:** All list APIs cursor- or offset-based with a cap (e.g. max 1000). Use `(tenant_id, created_at, id)` for stable cursor.
- **Indexes:** As above; avoid full table scans; all tenant queries use `tenant_id` in predicate.
- **Connection pooling:** Per-tenant limits optional; global pool size tuned for concurrent requests and RLS session setup.
- **Async for heavy reads:** Export/reports via queue + blob storage; API returns job id; no large result sets in HTTP.
- **Caching:** Cache tenant metadata and possibly per-tenant config; do **not** cache tenant-scoped entity lists at application layer (stale data risk). Use DB and indexes as source of truth.
- **Outbox:** For domain events, use transactional outbox table (same DB, same transaction as aggregate) then worker to publish to Kafka; avoids dual-write and keeps “eventually once” publishing.

---

## 7. Summary

- **Architecture:** Hexagonal with clear ports (repositories, event publisher, tenant resolver); DDD with AR as one bounded context and a shared kernel for AP/GL.
- **Multi-tenancy:** Single database with `tenant_id` on every table, RLS, and application-level enforcement (single resolution point, tenant on every repository call and event).
- **Scale:** Tenant-first indexing, UUID v7, pagination, outbox for events, optional read models and partitioning when needed.
- **Extensibility:** AP and GL as additional bounded contexts consuming shared kernel and AR (or AP) events, with their own modules and packages.

This gives a production-grade, multi-tenant AR backend that remains extensible and maintainable as you add AP and GL.
