# InvoiceGenie — Onboarding & Architecture Blueprint

> **Audience:** Engineers joining the project, AI agents, and reviewers who need a map of the system before changing code.  
> **Repo:** [timus97/InvoiceGenie](https://github.com/timus97/InvoiceGenie)  
> **Stack:** Java 17 · Quarkus 3.8.6 · Maven multi-module · PostgreSQL 15+ (H2 for local/dev) · DDD + Hexagonal Architecture  
> **Scope:** Multi-tenant **Accounts Receivable (AR)** backend only — no UI. Designed to extend later toward AP and GL.

---

## 1. What this product does

InvoiceGenie is a production-oriented AR API that:

| Capability | Summary |
|------------|---------|
| **Invoices** | Create/issue, list, get, mark overdue, write off, apply payment status, update due date |
| **Payments** | Record customer payments; allocate to invoices (FIFO or manual) |
| **Customers** | CRUD + block/unblock + credit check |
| **Cheques** | Lifecycle: RECEIVED → DEPOSITED → CLEARED or BOUNCED |
| **Aging** | Buckets and early-payment discount calculation (2%) |
| **Credit notes** | Early-payment discount credit notes + apply to payment |
| **Ledger** | Double-entry rules (AR / REVENUE / BANK / EXPENSE) — domain-ready; persistence is still thin |
| **Outbox** | Transactional outbox for domain events (Kafka publish path stubbed) |
| **Multi-tenancy** | Every request and every query is tenant-scoped |

There is **no frontend** in this repository. Test via Swagger, Postman, or `scripts/test-api.sh`.

---

## 2. Mental model (start here)

```
  Client (curl / Postman / Swagger)
           │
           │  X-Tenant-Id (required)
           ▼
  ┌──────────────────── ar-adapter-api ────────────────────┐
  │  Filters → REST Resource → (optional CDI Producer)     │
  └───────────────────────────┬────────────────────────────┘
                              │ inbound ports / domain services
                              ▼
  ┌──────────────────── ar-application ────────────────────┐
  │  Use cases orchestrate domain + outbound ports         │
  │  (invoice + payment paths are the most complete)       │
  └───────────────────────────┬────────────────────────────┘
                              │ domain model + repository ports
                              ▼
  ┌────────────────────── ar-domain ───────────────────────┐
  │  Aggregates, lifecycle engines, pure business rules    │
  │  ZERO framework / JDBC / HTTP                          │
  └───────────────────────────┬────────────────────────────┘
                              │ port interfaces
              ┌───────────────┼───────────────┐
              ▼               ▼               ▼
     persistence         messaging        shared-kernel
     (JPA adapters)    (outbox + Kafka)   (TenantId, Money…)
              └───────────────┬───────────────┘
                              ▼
                        ar-bootstrap
                   (Quarkus process + CDI wiring)
```

**Rules of thumb for new joiners:**

1. **Tenant always first** — no use case or repository call without `TenantId`.
2. **Invoices** are the most complete vertical slice (API → use case → domain → JPA → audit → outbox).
3. **Payments / allocations** are the second solid path.
4. **Cheques, aging, credit notes, ledger** are domain-rich but thinner at application/integration edges (REST often talks to domain services + repos directly).
5. Prefer **H2 profile (`dev`)** for local exploration; use **Postgres + SQL migration** for multi-tenant/RLS realism (after aligning schema with entities — see §12). Legacy alias: `sqlite` → same as `dev`.

---

## 3. Repository layout

```
InvoiceGenie/
├── pom.xml                      # Parent reactor (Java 17, Quarkus BOM 3.6.4)
├── docker-compose.yml           # app + postgres:15
├── Dockerfile                   # Container image (eval/sandbox style)
├── README.md                    # Quick start + API catalog
├── docs/
│   ├── ONBOARDING.md            # ← this document
│   ├── SCHEMA.md                # SQL schema documentation
│   └── sql/001_init_ar_schema.sql
├── postman/                     # Collection + local environment
├── scripts/                     # test-api.sh, push helpers
├── shared-kernel/               # Cross-cutting VOs
├── ar-domain/                   # Pure domain
├── ar-application/              # Use cases + ports
├── ar-adapter-api/              # REST + filters
├── ar-adapter-persistence/      # JPA entities + repository adapters
├── ar-adapter-messaging/        # Outbox publisher + worker
└── ar-bootstrap/                # Runnable Quarkus app
```

### Maven module dependency graph

```
                    shared-kernel
                          ▲
                          │
                      ar-domain
                          ▲
                          │
                   ar-application
                     ▲    ▲    ▲
                     │    │    │
        ar-adapter-api    │    ar-adapter-messaging
                     │    │    │
                     │    ar-adapter-persistence
                     │    │    │
                     └── ar-bootstrap ──┘
```

| Module | Packaging | Depends on | Role in hexagon |
|--------|-----------|------------|-----------------|
| `shared-kernel` | jar | (none) | Shared kernel |
| `ar-domain` | jar | shared-kernel | Domain core |
| `ar-application` | jar | ar-domain | Application services / ports |
| `ar-adapter-api` | jar | ar-application, ar-domain, (messaging for outbox admin) | **Driving** adapter |
| `ar-adapter-persistence` | jar | ar-domain | **Driven** adapter |
| `ar-adapter-messaging` | jar | ar-domain, ar-application | **Driven** adapter |
| `ar-bootstrap` | jar (Quarkus app) | all three adapters | Composition root |

**Intended boundary:** domain and application must not depend on adapters. Adapters implement domain/application ports.

---

## 4. Module deep-dives

### 4.1 `shared-kernel` — cross-cutting primitives

**Package:** `com.invoicegenie.shared.*`

| Type | Responsibility |
|------|----------------|
| `TenantId` | Strongly typed tenant UUID |
| `Money` | Amount + ISO 4217 currency; arithmetic with same-currency checks |
| `EntityId` | Base for typed IDs |
| `UuidV7` | Time-ordered UUID generation (sort-friendly, partition-friendly) |
| `DomainEvent` | Marker/contract for domain events (`eventId`, `tenantId`, `occurredAt`) |
| `TenantContext` | **ThreadLocal** holder for current tenant (set per request, cleared after) |
| `DbTenantContext` | Helper intended to set Postgres RLS GUC `app.current_tenant_id` |

**Must not** depend on domain, application, or infrastructure.

---

### 4.2 `ar-domain` — AR bounded context (pure)

**Package root:** `com.invoicegenie.ar.domain`

This module is the source of truth for business rules. Prefer putting new rules here rather than in REST resources.

#### Packages

| Package | Contents |
|---------|----------|
| `model.invoice` | `Invoice` aggregate, `InvoiceLine`, `InvoiceId`, `InvoiceStatus`, `InvoiceLifecycleEngine`, `InvoiceRepository` (port), aging report types |
| `model.payment` | `Payment`, `PaymentAllocation`, `PaymentId`, methods/statuses, `Cheque`, `ChequeRepository`, `PaymentRepository` |
| `model.customer` | `Customer`, `CustomerId`, `CustomerStatus`, `CustomerRepository` |
| `model.ledger` | `Account` (enum), `LedgerEntry`, `EntryType`, `LedgerRepository` |
| `model.outbox` | `OutboxEntry`, `OutboxStatus`, `OutboxRepository`, `AuditEntry`, `AuditRepository` |
| `event` | `InvoiceIssued`, `PaymentRecorded`, `PaymentAllocated` |
| `service` | Domain services: `PaymentAllocationEngine`, `ChequeService`, `LedgerService`, `AgingService`, `CreditNoteService`, `CustomerService`, `AllocationDomainService` |

#### Invoice lifecycle (`InvoiceLifecycleEngine`)

```
                    ┌──────────────┐
                    │    DRAFT     │
                    └──────┬───────┘
                           │ issue
                           ▼
                    ┌──────────────┐
          ┌────────│   ISSUED     │────────┐
          │        └──────┬───────┘        │
          │ partial pay   │ full pay       │ overdue
          ▼               ▼                ▼
   PARTIALLY_PAID ──►   PAID           OVERDUE
          │               │                │
          │               │ reopen*        │ write-off / pay
          │               ▼                ▼
          │            ISSUED         WRITTEN_OFF (terminal)
          └──────────────────────────────┘
* PAID → ISSUED allowed for cheque bounce reopen
```

Transitions that violate the engine throw `IllegalStateException` → API maps to **409 STATE_ERROR**.

#### Domain repository ports (interfaces live in domain)

Implemented by `ar-adapter-persistence`:

- `InvoiceRepository`
- `PaymentRepository`
- `CustomerRepository`
- `ChequeRepository`
- `CreditNoteRepository`
- `LedgerRepository`
- `OutboxRepository`
- `AuditRepository`

**Must not** depend on JPA, REST, Kafka, or Quarkus runtime (CDI annotations appear sparingly for producer-friendly types; domain logic itself is framework-agnostic).

---

### 4.3 `ar-application` — use cases & ports

**Package root:** `com.invoicegenie.ar.application`

#### Inbound ports (what the outside world can ask for)

| Port | Implementation | Purpose |
|------|----------------|---------|
| `IssueInvoiceUseCase` | `IssueInvoiceService` | Create lines, issue invoice, audit, publish `InvoiceIssued` |
| `GetInvoiceUseCase` | `GetInvoiceService` | Load single invoice by tenant + id |
| `ListInvoicesUseCase` | `ListInvoicesService` | Cursor pagination + optional status filter |
| `InvoiceLifecycleUseCase` | `InvoiceLifecycleService` | Issue / overdue / write-off / apply payment status / due date / reopen |
| `RecordPaymentUseCase` | `RecordPaymentService` | Record a payment against a customer |
| `PaymentAllocationUseCase` | `PaymentAllocationService` | FIFO + manual allocation, idempotency key |

#### Outbound ports (what application needs from infrastructure)

| Port | Typical adapter |
|------|-----------------|
| `EventPublisher` | `KafkaEventPublisher` (writes outbox, not Kafka directly) |
| `IdGenerator` | Anonymous bean in `ArApplication` → `UuidV7` → `InvoiceId` |

Repository ports are **domain** interfaces injected into application services.

#### Application-layer workflow pattern

```
UseCase.method(TenantId, command)
  1. Load aggregates via repository ports (always with tenant)
  2. Mutate via domain methods / engines
  3. Persist aggregates
  4. Optionally write audit + publish domain event
  5. Return id / result DTO-ish domain types
```

**Must not** depend on adapters, HTTP types, or JPA entities.

---

### 4.4 `ar-adapter-api` — driving adapter (REST)

**Package root:** `com.invoicegenie.ar.adapter.api`

#### Filters (order of concerns)

| Class | When | Behavior |
|-------|------|----------|
| `TenantFilter` | Request | Requires `X-Tenant-Id` header → `TenantId.of` → `TenantContext.setCurrentTenant`. Also binds Postgres RLS GUC via `DbTenantContext.setTenantLocal(EntityManager, tenantId)` when EM is available. Missing/invalid → **400** |
| `RequestLoggingFilter` | Request + response | Correlation-style request logging |
| `TenantContextClearFilter` | Response | Clears RLS GUC (best-effort) + `TenantContext.clear()` so ThreadLocal does not leak across threads |
| `GlobalExceptionMapper` | Errors | `IllegalArgumentException` → 400 `VALIDATION_ERROR`; `IllegalStateException` → 409 `STATE_ERROR`; else 500 |

#### Postgres RLS tenant hardening

| Layer | Behavior |
|-------|----------|
| **App-level (always)** | Every repository query filters by `tenant_id`. This is the primary isolation mechanism. |
| **DB-level (Postgres)** | Schema RLS policies read `app.current_tenant_id`. `TenantFilter` sets it with `SELECT set_config('app.current_tenant_id', :tid, true)` (transaction-local) via `DbTenantContext`. |
| **H2 / local dev** | GUC calls fail and are **gracefully ignored** (no-op). App-level isolation still applies. |
| **Helper** | `com.invoicegenie.shared.tenant.DbTenantContext` — JDBC `setTenant`/`clearTenant` and EntityManager `setTenantLocal`/`clearTenant`. |

> **Note:** `SET LOCAL` / `set_config(..., true)` is most effective inside an active transaction on the same connection used by JPA. Connection pooling can limit effectiveness until a Hibernate statement inspector is added; app-level filters remain authoritative.

#### REST resources → collaborators

| Resource | Base path | Primary collaborator(s) | Layer style |
|----------|-----------|-------------------------|-------------|
| `InvoiceResource` | `/api/v1/invoices` | Invoice use cases | Full hexagonal |
| `PaymentResource` | `/api/v1/payments` | `RecordPaymentUseCase`, `PaymentAllocationUseCase` | Full hexagonal |
| `CustomerResource` | `/api/v1/customers` | `CustomerService` + repos | Domain service from REST |
| `ChequeResource` | `/api/v1/cheques` | `ChequeService` + `ChequeRepository` + lifecycle use case | Hybrid |
| `AgingResource` | `/api/v1/aging` | `AgingService` (partial; report endpoint stubbed) | Hybrid |
| `CreditNoteResource` | `/api/v1/credit-notes` | `CreditNoteService` | Hybrid |
| `LedgerResource` | `/api/v1/ledger` | `LedgerService` / ledger repo | Hybrid |
| `OutboxResource` | `/api/v1/outbox` | `OutboxWorker` (admin/process/stats) | Ops |

#### CDI producers in this module

Some domain services are `@Vetoed` (not auto-beans). Producers wire them:

- `PaymentAllocationProducer` — payment use cases
- `ChequeProducer` — `ChequeService`
- `LedgerProducer` — ledger domain service
- `AgingProducer` — aging domain service

Invoice use cases are produced in **`ar-bootstrap`** (`ArApplication`), not here.

---

### 4.5 `ar-adapter-persistence` — driven adapter (DB)

**Package root:** `com.invoicegenie.ar.adapter.persistence`

| Layer | Classes |
|-------|---------|
| **Entities** | `InvoiceEntity`, `InvoiceLineEntity`, `PaymentEntity`, `PaymentAllocationEntity`, `CustomerEntity`, `ChequeEntity`, `CreditNoteEntity`, `OutboxEntity`, `AuditLogEntity` |
| **Mappers** | `InvoiceMapper`, `PaymentMapper`, `CustomerMapper`, `ChequeMapper`, `CreditNoteMapper` — domain ↔ entity |
| **Adapters** | `*RepositoryAdapter` implementing domain ports via `EntityManager` / queries |

**Invariants enforced in adapters:**

- Every query filters by `tenant_id`.
- Prefer `findByTenantAndId(tenantId, id)` over bare `findById`.
- Invoice save replaces line items (delete-then-persist lines for that invoice).

**Important implementation note — ledger:**  
`LedgerRepositoryAdapter` is currently **in-memory** (`ConcurrentHashMap`), not mapped to `ar_ledger_entry`. Domain ledger rules exist; durable GL persistence is incomplete.

**Schema tables without JPA entities (today):**  
`ar_tenant`, `ar_invoice_version`, `ar_account`, `ar_ledger_entry`, `ar_exchange_rate`.

---

### 4.6 `ar-adapter-messaging` — driven adapter (events)

| Class | Role |
|-------|------|
| `KafkaEventPublisher` | Implements `EventPublisher`. Serializes domain events with **Jackson `ObjectMapper`** → `OutboxEntry` → `OutboxRepository.save` in same transaction. Aggregate type/id resolved via an **event type registry** (not long instanceof chains). |
| `OutboxWorker` | `@Scheduled` poller (`outbox.poll-interval`, default 5s, delayed start). Loads PENDING → PROCESSING → PUBLISHED (or FAILED with retries). Cleanup cron deletes old published rows. |
| `OutboxKafkaSender` | Optional CDI interface. When `outbox.kafka-enabled=true` **and** a bean is present, worker calls `send(entry)`; otherwise logs (safe default). |

**Config (`application.yml`):**

| Key | Default | Meaning |
|-----|---------|---------|
| `outbox.enabled` | `true` | Worker poll loop on/off |
| `outbox.kafka-enabled` | `false` | Attempt real Kafka emit via `OutboxKafkaSender` |
| `outbox.batch-size` / `poll-interval` / `cleanup-*` | see yml | Worker tuning |

To enable Kafka in production: set `outbox.kafka-enabled=true`, provide an `OutboxKafkaSender` bean wired to `@Channel("outbox-events") Emitter`, and uncomment `mp.messaging.outgoing.outbox-events` (smallrye-kafka) in `application.yml`.

**Events handled for aggregate typing (registry):**

| Domain event | Aggregate type in outbox | Published by |
|--------------|--------------------------|--------------|
| `InvoiceIssued` | `INVOICE` | `IssueInvoiceService` |
| `PaymentRecorded` | `PAYMENT` | `RecordPaymentService` |
| `PaymentAllocated` | `PAYMENT` | `PaymentAllocationService` (FIFO + manual) |

---

### 4.7 `ar-bootstrap` — composition root

| Artifact | Purpose |
|----------|---------|
| `com.invoicegenie.ar.ArApplication` | CDI `@Produces` for invoice use cases + `IdGenerator` |
| `application.yml` | Datasource, HTTP, logging, outbox, profiles |
| `EndToEndWorkflowTest` | `@QuarkusTest` + RestAssured happy-path workflow |

This module should stay free of business rules — only wiring and runtime config.

---

## 5. End-to-end workflows (how modules interact)

### 5.1 Create + issue invoice (canonical happy path)

```
POST /api/v1/invoices
Headers: X-Tenant-Id, Content-Type: application/json
Body: { invoiceNumber, customerRef, currencyCode, dueDate, lines[{description, amount}] }
```

**Sequence:**

```
1. TenantFilter
      └─ TenantContext.setCurrentTenant(X-Tenant-Id)

2. InvoiceResource.create(dto)
      └─ IssueInvoiceUseCase.issue(tenantId, IssueInvoiceCommand)
           [IssueInvoiceService]
           a. IdGenerator.newInvoiceId()          → UuidV7
           b. Build InvoiceLine list from DTO     → Money per line
           c. new Invoice(...) + invoice.issue()  → DRAFT→ISSUED (lifecycle engine)
           d. InvoiceRepository.save              → InvoiceRepositoryAdapter
                · mapper.toEntity → InvoiceEntity.merge
                · replace InvoiceLineEntity rows
           e. AuditRepository.save(AuditEntry)    → AuditRepositoryAdapter / ar_audit_log
           f. EventPublisher.publish(InvoiceIssued)
                · KafkaEventPublisher
                · OutboxRepository.save(PENDING)  → ar_outbox

3. Response 201 { id }
4. TenantContextClearFilter.clear()

Async (background):
5. OutboxWorker.processPendingEvents (every ~5s)
      └─ PENDING → PROCESSING → (would publish Kafka) → PUBLISHED
```

**Modules touched:** api → application → domain → persistence + messaging (via port).

---

### 5.2 Invoice lifecycle (without re-creating)

| HTTP | Application | Domain effect |
|------|--------------|---------------|
| `POST /api/v1/invoices/{id}/issue` | `InvoiceLifecycleService.issue` | `Invoice.issue()` if still DRAFT |
| `POST /api/v1/invoices/{id}/overdue?today=` | `markOverdue` | ISSUED/PARTIALLY_PAID → OVERDUE when past due |
| `POST /api/v1/invoices/{id}/writeoff` | `writeOff` | → WRITTEN_OFF (+ reason audit) |
| `POST /api/v1/invoices/{id}/payment` | `applyPayment` | Status-only: PARTIALLY_PAID / PAID (**no Payment aggregate**) |
| `PATCH /api/v1/invoices/{id}/due-date` | update due date | Domain validation |
| `DELETE /api/v1/invoices/{id}` | — | **405** — use write-off |

---

### 5.3 Record payment + allocate (FIFO / manual)

**A. Record payment**

```
POST /api/v1/payments
  → PaymentResource
  → RecordPaymentService
       · CustomerRepository.findByTenantAndId (must exist)
       · PaymentRepository.findByTenantAndNumber (duplicate guard)
       · new Payment(... status RECEIVED ...)
       · PaymentRepository.save + AuditRepository.save
  → 201 { id, paymentNumber }
```

**B. FIFO auto-allocation**

```
POST /api/v1/payments/{id}/allocate/fifo
Headers: X-Tenant-Id, Idempotency-Key (optional)
Body: { allocatedBy, version? }

  → PaymentAllocationService.autoAllocateFIFO
       1. Idempotency cache lookup (in-memory ConcurrentHashMap; process-local)
       2. Load Payment
       3. InvoiceRepository.findOpenByTenantAndCustomer(tenant, customerId)
          ⚠ Open invoices match when invoice.customerRef equals customer UUID string
       4. PaymentAllocationEngine.autoAllocateFIFO
          · sort by dueDate, then issueDate
          · allocate until payment unallocated = 0 or invoices exhausted
       5. Payment.allocate(...) mutates aggregate
       6. Save payment + update each invoice payment status (PAID / PARTIALLY_PAID)
```

**C. Manual allocation**

```
POST /api/v1/payments/{id}/allocate/manual
Body: { allocatedBy, version?, allocations: [{ invoiceId, amount, notes? }] }

  → PaymentAllocationEngine.manualAllocate
  → same persist pattern as FIFO
```

**Modules:** api → application → domain engines → persistence.  
**Events:** `RecordPaymentService` publishes `PaymentRecorded`; allocation paths publish `PaymentAllocated` into the outbox via `EventPublisher`.

---

### 5.4 Cheque lifecycle

```
RECEIVED ──deposit──► DEPOSITED ──clear──► CLEARED
                         │
                         └──bounce──► BOUNCED
```

| Step | HTTP | Domain | Side effects in code today |
|------|------|--------|----------------------------|
| Create | `POST /api/v1/cheques` | `new Cheque` RECEIVED | Persist via `ChequeRepository` |
| Deposit | `POST .../deposit` | `ChequeService.deposit` | Status DEPOSITED, save |
| Clear | `POST .../clear` | `ChequeService.clear` | Status CLEARED; builds ledger entries **in memory** (not always durable) |
| Bounce | `POST .../bounce` | `ChequeService.bounce` | Status BOUNCED; reverse ledger in memory; `InvoiceLifecycleUseCase.reopen` for affected invoices |

**Interaction:** `ChequeResource` + `ChequeProducer` + domain `ChequeService` + persistence + invoice lifecycle use case (for reopen). Crosses api → domain → persistence → application (lifecycle only).

---

### 5.5 Aging & early-payment discount

| Endpoint | Behavior |
|----------|----------|
| `GET /api/v1/aging` | Resource currently returns a **stub/empty** report (domain `AgingService.generateAgingReport` exists for full calc) |
| `GET /api/v1/aging/buckets` | Bucket labels (0–30, 31–60, 61–90, 90+) |
| `POST /api/v1/aging/discount/calculate` | **2%** early-payment discount via `AgingService` |

Buckets feed credit-note generation workflows (early payment discount → `CreditNoteService`).

---

### 5.6 Credit notes

```
POST /api/v1/credit-notes
  → CreditNoteService.generateEarlyPaymentDiscount / create
  → CreditNoteRepository.save

POST /api/v1/credit-notes/{id}/apply
  → creditNote.apply(paymentId)
  → save
```

Domain owns validity and status transitions; REST is a thin shell.

---

### 5.7 Ledger (double-entry rules)

`LedgerService` encodes journal intent:

| Business event | Debit | Credit |
|----------------|-------|--------|
| Invoice issued | AR | REVENUE |
| Payment received | BANK | AR |
| Write-off | EXPENSE | AR |

`Account` is a **domain enum**, not rows from `ar_account`.  
Query APIs under `/api/v1/ledger/*` read balances/entries from the (currently in-memory) ledger repository.  
**Gap:** main invoice issue/pay/write-off application paths do **not** automatically post durable ledger entries yet — domain tests cover the rules.

---

### 5.8 Outbox + messaging interaction

```
[Write side - same DB transaction as aggregate]
  DomainEvent → EventPublisher → KafkaEventPublisher
       → OutboxEntry(PENDING) → ar_outbox

[Read/publish side - scheduled]
  OutboxWorker
       → findPending(batch)
       → mark PROCESSING
       → publish to Kafka  (TODO / stub)
       → mark PUBLISHED or FAILED (+ retry_count)
       → periodic cleanup of old PUBLISHED
```

Config keys (`application.yml`):

```yaml
outbox:
  enabled: true
  batch-size: 100
  poll-interval: 5s
  delay-start: 10s
  cleanup-days: 7
```

Kafka SmallRye connectors in config are **commented out** until a broker is provisioned.

---

## 6. Multi-tenancy design

### Layers of isolation

| Layer | Mechanism | Status |
|-------|-----------|--------|
| **Transport** | Required `X-Tenant-Id` UUID header | Enforced |
| **Application** | `TenantContext` ThreadLocal | Enforced + cleared |
| **Repositories** | Explicit `TenantId` param + `WHERE tenant_id = ?` | Enforced in adapters |
| **Database** | `tenant_id` on every table; RLS policies in SQL | Schema ready; **session GUC not set per request yet** |
| **Events** | `tenantId` on every `DomainEvent` / outbox row | Enforced on write |

### Request lifecycle for tenant

```
Request
  → TenantFilter resolves header
  → TenantContext.setCurrentTenant
  → Resource reads TenantContext.getCurrentTenant()
  → Use case / service receives TenantId explicitly
  → Adapter queries always include tenantId
  → TenantContextClearFilter.clear()
```

Never introduce a repository method that loads by id alone without tenant.

---

## 7. Data model blueprint

### Domain aggregates (logical)

```
Tenant (registry)
  └── Customer 1──* Invoice 1──* InvoiceLine
  │                  │
  │                  *── PaymentAllocation ──* Payment
  ├── Cheque (optional payment instrument)
  ├── CreditNote
  ├── LedgerEntry (GL-style)
  ├── OutboxEntry (integration)
  └── AuditEntry (compliance)
```

### Physical tables (`docs/sql/001_init_ar_schema.sql`)

| Table | Role |
|-------|------|
| `ar_tenant` | Tenant registry, base currency, settings |
| `ar_customer` | Customer master per tenant |
| `ar_invoice` | Invoice current state (versioned) |
| `ar_invoice_version` | Immutable JSONB snapshots (schema present; app path thin) |
| `ar_invoice_line` | Lines keyed by (tenant, invoice, sequence) |
| `ar_payment` | Payments received |
| `ar_payment_allocation` | Payment ↔ invoice amounts |
| `ar_cheque` / `ar_credit_note` | Instruments / adjustments |
| `ar_account` / `ar_ledger_entry` | Chart + double-entry (schema; app uses enum + in-memory) |
| `ar_audit_log` | Before/after audit |
| `ar_outbox` | Transactional outbox |
| `ar_exchange_rate` | Optional FX (schema-only today) |

IDs are **UUID v7** in application code. Money uses **NUMERIC(19,2)** / `Money` VO.

Full commentary: `docs/SCHEMA.md`.

---

## 8. Configuration & runtime profiles

File: `ar-bootstrap/src/main/resources/application.yml`

| Profile | Database | Hibernate DDL | Typical use |
|---------|----------|---------------|-------------|
| **default** | PostgreSQL `jdbc:postgresql://localhost:5432/invoicegenie` user/pass `ar`/`ar` | `none` (apply SQL manually) | Local/prod-like |
| **`%dev`** | **H2** in-memory `jdbc:h2:mem:testdb` | `update` | Fast local dev (preferred) |
| **`%sqlite`** | Parent of `%dev` (alias) | same as `%dev` | Legacy name only — **not** SQLite |
| **`%test`** | H2 mem | `update` | Automated tests |

Default HTTP port in config: **8080** for all profiles. On machines where 8080 is occupied, override with `-Dquarkus.http.port=...`.

### Docker Compose

```yaml
# docker-compose.yml
services:
  postgres:  # postgres:15-alpine, user/pass/db ar/ar/invoicegenie, port 5432
  app:       # build Dockerfile, port 8080, depends_on postgres
```

If host port 5432 is already taken, map Postgres to another host port and point JDBC URL accordingly.

### Required headers

| Header | Required | Purpose |
|--------|----------|---------|
| `X-Tenant-Id` | **Yes** | Tenant UUID |
| `Content-Type: application/json` | Body requests | JSON |
| `Idempotency-Key` | Optional | Honored for **payment allocation** (in-memory); accepted but unused on invoice create |

### Observability endpoints

- Health: `/q/health`
- OpenAPI: `/q/openapi`
- Swagger UI: `/q/swagger-ui/`
- Logs: console + `logs/invoicegenie.log` (file logging under working dir)

---

## 9. Testing strategy

| Module | What is tested | Style |
|--------|----------------|-------|
| `shared-kernel` | Money, TenantId, UuidV7, TenantContext | Unit |
| `ar-domain` | Lifecycle, allocation engine, cheque, ledger, aging, events | Unit (no DB) |
| `ar-application` | All six application services | Unit + Mockito |
| `ar-adapter-persistence` | Entities, mappers, adapters | Unit/integration-style |
| `ar-adapter-messaging` | Publisher + worker | Unit |
| `ar-adapter-api` | *(no dedicated test sources)* | — |
| `ar-bootstrap` | `EndToEndWorkflowTest` | `@QuarkusTest` + RestAssured + H2 |

```bash
mvn test
mvn -pl ar-domain test
mvn -pl ar-adapter-persistence test
mvn -pl ar-bootstrap test

# Coverage (JaCoCo — reports only; minimum not enforced yet)
./scripts/coverage.sh          # Linux/macOS/Git Bash
./scripts/coverage.ps1         # Windows PowerShell
# Reports: */target/site/jacoco/index.html and target/site/jacoco-aggregate/

# Dependency hygiene
mvn dependency:tree
mvn versions:display-dependency-updates
```

Manual: Postman collection under `postman/`, or API scripts:

```bash
# Start with profile=dev (H2), then:
./scripts/test-api.sh http://localhost:8080
./scripts/test-api.ps1 -BaseUrl http://localhost:8080   # Windows
```

---

## 10. Local development quick reference

### Prerequisites

- JDK **17+** (17 recommended to match `maven.compiler.source`)
- Maven **3.9+** (avoid broken Maven installs on PATH)
- Optional: Docker/Rancher Desktop for Postgres
- Optional: Kafka only if you enable messaging connectors

### Build

```bash
mvn clean install
# or skip tests for a faster compile
mvn clean install -DskipTests
```

### Prerequisites (local)

| Tool | Version | Required? |
|------|---------|-----------|
| JDK | 17+ | Yes |
| Maven | 3.9+ | Yes |
| Docker / Postgres 15+ | — | Optional (default profile only) |
| Kafka | — | No (`outbox.kafka-enabled=false` by default) |

### Run (H2 — no Postgres, preferred)

```bash
mvn clean install -DskipTests
mvn -pl ar-bootstrap -Dquarkus.profile=dev -Dquarkus.kafka.devservices.enabled=false quarkus:dev

# Helpers
./scripts/dev-up.sh          # Linux/macOS/Git Bash
./scripts/dev-up.ps1         # Windows PowerShell
```

### Run (Postgres)

```bash
docker compose up -d postgres
# apply docs/sql/001_init_ar_schema.sql then align columns if needed (§12)
mvn -pl ar-bootstrap -Dquarkus.kafka.devservices.enabled=false quarkus:dev
```

### PowerShell note

Quote `-D` properties so PowerShell does not parse them:

```powershell
mvn -pl ar-bootstrap "-Dquarkus.profile=dev" "-Dquarkus.http.port=8082" quarkus:dev
```

### Smoke test

```bash
curl -s http://localhost:8080/q/health
curl -s -X POST http://localhost:8080/api/v1/invoices \
  -H "Content-Type: application/json" \
  -H "X-Tenant-Id: 00000000-0000-0000-0000-000000000001" \
  -d "{\"invoiceNumber\":\"INV-1\",\"customerRef\":\"C1\",\"currencyCode\":\"USD\",\"dueDate\":\"2026-12-31\",\"lines\":[{\"description\":\"Svc\",\"amount\":100}]}"
```

---

## 11. Where to change what (decision guide)

| You want to… | Prefer changing… | Avoid… |
|--------------|------------------|--------|
| Add a business rule (status, money, allocation) | `ar-domain` | REST resource if-blocks |
| Add a new API use case (orchestration) | Inbound port + service in `ar-application` | Putting orchestration only in Resource |
| Expose HTTP | `ar-adapter-api` resource + DTO | Domain types leaking framework annotations |
| Change SQL/JPA mapping | `ar-adapter-persistence` entity/mapper/adapter + SQL migration | Domain depending on columns |
| Change event delivery | `ar-adapter-messaging` | Publishing Kafka from application services directly |
| Wire new use case into CDI | `ArApplication` or a `*Producer` | Field injection cycles across adapters |
| Shared money/tenant primitives | `shared-kernel` | Duplicating Money/TenantId in modules |

---

## 12. Known gaps & architecture debt (read before “fixing” bugs)

These are intentional maturity gaps, not always accidental bugs:

1. **`%dev` is H2 in-memory** (preferred). Legacy **`%sqlite` is an alias** of `%dev` — still not a SQLite file DB.
2. **SQL vs JPA naming:** e.g. `ar_invoice.currency` vs entity `currency_code`; payment `version` column; invoice `customer_id` NOT NULL in SQL vs entity using only `customer_ref`.
3. **RLS** defined in SQL but request path does not set `app.current_tenant_id` yet.
4. **Ledger** durable tables unused; adapter is in-memory; main flows don’t auto-post.
5. **Outbox** write path works; Kafka publish is stubbed; connectors commented out.
6. **Idempotency** only in-memory for allocations; invoice `Idempotency-Key` not applied.
7. **Aging GET report** returns empty/demo data; real logic lives in domain + unit tests.
8. **Hexagonal purity:** some REST resources call domain services directly (cheques, aging, credit notes, customers) instead of application use cases.
9. **`ar-adapter-api` depends on messaging** for outbox admin endpoints (boundary smell).
10. **Invoice create always issues** (no pure DRAFT create API path despite DRAFT in lifecycle).
11. **FIFO customer matching** expects `customerRef` ≈ customer UUID string for open-invoice lookup.
12. README links `docs/AR_BACKEND_DESIGN.md` — not present; this onboarding doc + `SCHEMA.md` are the living blueprint.

---

## 13. Glossary

| Term | Meaning here |
|------|----------------|
| **Aggregate** | Consistency boundary (e.g. Invoice + lines) |
| **Port** | Interface defining a dependency (inbound use case or outbound repo/publisher) |
| **Adapter** | Infrastructure implementing a port (REST, JPA, outbox) |
| **Tenant** | Isolated customer org; all data partitioned by `tenant_id` |
| **Outbox** | Table storing events in the same TX as business data for reliable publish |
| **FIFO allocation** | Apply payment to oldest open invoices first |
| **UUID v7** | Time-ordered UUID used for ids |
| **RLS** | Postgres row-level security policies |

---

## 14. Suggested first week for a new engineer

1. Read this document + skim `docs/SCHEMA.md`.
2. Run with H2 profile; open Swagger; create an invoice; list it.
3. Trace **one** create-invoice request in the debugger:  
   `TenantFilter` → `InvoiceResource` → `IssueInvoiceService` → `Invoice` → `InvoiceRepositoryAdapter` → `KafkaEventPublisher`.
4. Read `InvoiceLifecycleEngine` and domain tests under `ar-domain/src/test`.
5. Run `mvn -pl ar-domain,ar-application test`.
6. Walk payment allocation engine + `PaymentAllocationService`.
7. Only then pick a ticket in a thinner area (cheque, aging, ledger) — and expect more wiring work.

---

## 15. Document maintenance

| When you change… | Update… |
|------------------|---------|
| Module boundaries / new module | §3–4 of this doc |
| HTTP surface | §5 + README endpoint tables |
| Schema | `docs/sql/*`, `docs/SCHEMA.md`, §7 + §12 |
| Profiles / ports | §8 + README Quick Start |
| Maturity of stubs (Kafka, ledger, RLS) | §12 |

---

*Generated as the project onboarding blueprint. Complements `README.md` (ops/API catalog) and `docs/SCHEMA.md` (data design).*
