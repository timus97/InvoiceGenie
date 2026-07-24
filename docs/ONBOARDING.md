# InvoiceGenie — Onboarding & Architecture Blueprint

> **Audience:** Engineers joining the project, AI agents, and reviewers who need a map of the system before changing code.  
> **Repo:** [timus97/InvoiceGenie](https://github.com/timus97/InvoiceGenie)  
> **Stack:** Java 17 · **Quarkus 3.27.3 LTS** · Maven multi-module · PostgreSQL 15+ (H2 for local/dev) · Flyway V1–V7 · Next.js web console · DDD + Hexagonal Architecture  
> **Scope:** Multi-tenant **Accounts Receivable (AR)** API + operator console under `web/`. Designed to extend later toward AP and GL.  
> **Last audited against code:** 2026-07-24

**Related product docs (read these for prioritization):**

| Doc | Role |
|-----|------|
| [PRODUCT_OWNER_STORIES.md](./PRODUCT_OWNER_STORIES.md) | Ordered eng stories, acceptance criteria, status |
| [FEATURE_PRIORITY_BACKLOG.md](./FEATURE_PRIORITY_BACKLOG.md) | P0–P3 residual backlog + maturity snapshot |
| [PRODUCTION_READINESS.md](./PRODUCTION_READINESS.md) | Prod requirements + machine verification |
| [SCHEMA.md](./SCHEMA.md) | Data model commentary |
| [README.md](../README.md) | Quick start + API catalog |

---

## 1. What this product does

InvoiceGenie is a production-oriented multi-tenant AR stack:

| Capability | Summary | Maturity (2026-07-24) |
|------------|---------|------------------------|
| **Invoices** | Create (issue or pure DRAFT), list/get, lifecycle, due-date PATCH, draft line PATCH, version snapshots, write-off | Strong |
| **Payments** | Record, list/get, FIFO + manual allocate, reverse/refund paths | Strong / good |
| **Customers** | CRUD, block/unblock, credit check, AR summary | Strong |
| **Cheques** | Lifecycle RECEIVED → DEPOSITED → CLEARED/BOUNCED; OCR parse helpers | Good |
| **Aging** | Live report + buckets + early-payment discount (2%) via application use case | Wired |
| **Credit notes** | Create / apply (AR impact still deepening — see residual stories) | Partial–good |
| **Ledger** | Double-entry rules; **JPA durable** posts on issue / pay / write-off / cheque | Wired |
| **Idempotency** | DB-backed store + retention cleanup job | Wired |
| **Outbox + Kafka** | Transactional outbox; optional `SmallRyeOutboxKafkaSender` when `OUTBOX_KAFKA_ENABLED=true` | Optional emit |
| **Webhooks** | Subscriptions + HTTP delivery worker (HMAC, SSRF guard, retries, delivery log) | Wired |
| **Auth** | API-key and HS256 JWT gate; JWT `roles[]` + path RBAC; prod fail-closed | Phase-1 |
| **Multi-tenancy** | `X-Tenant-Id`, app filters, Postgres RLS GUC + Agroal pool interceptor | Hardened |
| **Web UI** | Next.js console: customers, invoices, payments, cheques, aging, ledger, audit, webhooks, settings | Present |
| **Migrations** | Flyway `V1`–`V7` under `ar-bootstrap/.../db/migration` | Prod default |

---

## 2. Mental model (start here)

```
  Browser (web/)  or  Client (curl / Postman / Swagger)
           │
           │  X-Tenant-Id  (+ X-API-Key / Bearer when security on)
           ▼
  ┌──────────────────── ar-adapter-api ────────────────────┐
  │  AuthFilter → TenantFilter → RoleAuthorizationFilter   │
  │  REST Resource → inbound ports                         │
  └───────────────────────────┬────────────────────────────┘
                              │ use cases
                              ▼
  ┌──────────────────── ar-application ────────────────────┐
  │  Application services orchestrate domain + outbound    │
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
     (JPA + Flyway)   (outbox/Kafka/    (TenantId, Money,
      + RLS interceptor  webhooks)        ActorContext…)
              └───────────────┬───────────────┘
                              ▼
                        ar-bootstrap
              (Quarkus process, CDI wiring, outbox admin)
```

**Rules of thumb:**

1. **Tenant always first** — no use case or repository call without `TenantId`.
2. **Prefer application ports** — REST resources depend on inbound use cases (hexagonal cleanup landed).
3. **Local exploration:** `quarkus.profile=dev` (H2). **Prod-like:** Postgres + Flyway + `%prod` security.
4. Prioritize work from [PRODUCT_OWNER_STORIES.md](./PRODUCT_OWNER_STORIES.md) / residual table in [FEATURE_PRIORITY_BACKLOG.md](./FEATURE_PRIORITY_BACKLOG.md), not historical gap lists in older doc revisions.

---

## 3. Repository layout

```
InvoiceGenie/
├── pom.xml                      # Parent reactor (Java 17, Quarkus BOM 3.27.3)
├── docker-compose.yml           # App + web + Postgres (env-driven secrets)
├── docker-compose.prod.yml      # Prod-hardening overlay (TLS edge + private DB)
├── Dockerfile.prod              # Multi-stage JVM production image
├── Dockerfile.arena             # Sandbox/eval image only
├── README.md                    # Quick start + API catalog
├── docs/
│   ├── ONBOARDING.md            # ← this document
│   ├── PRODUCT_OWNER_STORIES.md
│   ├── FEATURE_PRIORITY_BACKLOG.md
│   ├── PRODUCTION_READINESS.md
│   ├── SCHEMA.md
│   ├── deploy/                  # nginx TLS sample, prod edge notes
│   └── sql/                     # Historical SQL (Flyway is source of truth)
├── postman/
├── scripts/                     # dev-up, test-api, coverage, security-scan
├── web/                         # Next.js AR console
├── shared-kernel/
├── ar-domain/
├── ar-application/
├── ar-adapter-api/
├── ar-adapter-persistence/
├── ar-adapter-messaging/
└── ar-bootstrap/                # Runnable Quarkus app + Flyway migrations
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

| Module | Packaging | Depends on | Role |
|--------|-----------|------------|------|
| `shared-kernel` | jar | (none) | Shared kernel VOs + tenant/actor context |
| `ar-domain` | jar | shared-kernel | Domain core (pure) |
| `ar-application` | jar | ar-domain | Use cases + ports |
| `ar-adapter-api` | jar | ar-application, ar-domain | Driving REST + filters + security |
| `ar-adapter-persistence` | jar | ar-domain, ar-application (idempotency port) | Driven JPA adapters |
| `ar-adapter-messaging` | jar | ar-domain, ar-application | Outbox, Kafka sender, webhooks |
| `ar-bootstrap` | Quarkus app | all adapters | Composition root + Flyway + outbox admin |

---

## 4. Module deep-dives

### 4.1 `shared-kernel`

| Type | Responsibility |
|------|----------------|
| `TenantId`, `Money`, `EntityId`, `UuidV7` | Strongly typed primitives |
| `DomainEvent` | Event contract |
| `TenantContext` | ThreadLocal current tenant |
| `DbTenantContext` | Postgres RLS GUC `app.current_tenant_id` |
| `ActorContext` | Actor id / roles for audit enrichment |

### 4.2 `ar-domain`

Packages: `model.invoice`, `model.payment`, `model.customer`, `model.ledger`, `model.outbox`, `model.webhook`, `event`, `service`, `exception`.

**Invoice lifecycle (`InvoiceLifecycleEngine`):**

```
DRAFT → ISSUED → PARTIALLY_PAID → PAID
              ↘ OVERDUE → WRITTEN_OFF
* PAID → ISSUED allowed for cheque bounce reopen
```

Domain repository ports (implemented in persistence): Invoice, Payment, Customer, Cheque, CreditNote, Ledger, Outbox, Audit, Tenant, ExchangeRate, Webhook (+ delivery), Idempotency (application port).

### 4.3 `ar-application`

Inbound ports (representative): `IssueInvoice`, `List/GetInvoice`, `InvoiceLifecycle`, `InvoiceVersion`, `RecordPayment`, `PaymentAllocation`, `PaymentQuery`, `PaymentReversal`, `Customer`, `Cheque`, `ChequeOcr`, `Aging`, `CreditNote`, `LedgerQuery`, `AuditQuery`, `Tenant`, `ExchangeRate`, `Webhook`.

Outbound ports: `EventPublisher`, `IdGenerator`, `IdempotencyStore`.

### 4.4 `ar-adapter-api`

| Filter | Role |
|--------|------|
| `AuthFilter` | Optional API-key / JWT when security enabled |
| `TenantFilter` | Requires `X-Tenant-Id`; sets `TenantContext` + RLS GUC |
| `RoleAuthorizationFilter` | Path / `@RequireRoles` checks |
| `TenantContextClearFilter` | Clears tenant + actor ThreadLocals |
| `RequestLoggingFilter` | Request/response logging |
| `GlobalExceptionMapper` | Domain/validation → HTTP error bodies |

REST resources under `/api/v1/*`: invoices, payments, customers, cheques (+ OCR), aging, credit-notes, ledger, audit, tenants, exchange-rates, webhooks.

### 4.5 `ar-adapter-persistence`

- **Entities:** Invoice (+ lines, versions), Payment (+ allocations), Customer, Cheque, CreditNote, LedgerEntry, Outbox, AuditLog, Tenant, ExchangeRate, Idempotency, Webhook, WebhookDelivery.
- **Adapters:** `*RepositoryAdapter` implementing domain ports via JPA; **every query tenant-scoped**.
- **Ledger:** `LedgerRepositoryAdapter` is **JPA** against durable ledger tables (not in-memory).
- **Idempotency:** `IdempotencyStoreAdapter` + `IdempotencyCleanupJob` (retention cron).
- **RLS:** `TenantConnectionInterceptor` (Agroal) rebinds `app.current_tenant_id` on connection acquire/return for Postgres.

### 4.6 `ar-adapter-messaging`

| Class | Role |
|-------|------|
| `KafkaEventPublisher` | `EventPublisher` → serialize → outbox `PENDING` in same TX |
| `OutboxWorker` | Scheduled poll: PENDING → PROCESSING → PUBLISHED/FAILED |
| `OutboxKafkaSender` / `SmallRyeOutboxKafkaSender` | Optional real Kafka emit when `outbox.kafka-enabled=true` |
| `WebhookDispatcher` | HTTP delivery to subscriptions (HMAC, SSRF checks, retries) |
| `WebhookSignature` / `SsrfUrlValidator` | Security helpers for webhooks |

### 4.7 `ar-bootstrap`

- `ArApplication` — CDI producers / composition root
- `application.yml` — datasource, Flyway, security, outbox, webhooks, profiles
- `db/migration` — Flyway **V1–V7**
- `ops.OutboxResource` — `/api/v1/outbox` admin (kept out of api→messaging dep)
- `@QuarkusTest` E2E workflow tests (H2)

---

## 5. End-to-end workflows

### 5.1 Create + issue invoice (canonical)

```
POST /api/v1/invoices
Headers: X-Tenant-Id, Content-Type: application/json, Idempotency-Key (optional)
Body: { invoiceNumber, customerId, currencyCode, dueDate, lines[...], issueImmediately? }
```

- Default `issueImmediately=true` (backward compatible): creates ISSUED + ledger + outbox `InvoiceIssued`.
- `issueImmediately=false`: pure **DRAFT** (no issue ledger); later `POST .../issue` or PATCH draft lines.
- Blocked/deleted customers rejected; credit limit enforced on issue (soft-warn on draft overage).

**Async:** `OutboxWorker` → optional Kafka; `WebhookDispatcher` → customer HTTP callbacks.

### 5.2 Invoice lifecycle

| HTTP | Effect |
|------|--------|
| `POST .../issue` | DRAFT → ISSUED (+ ledger) |
| `POST .../overdue?today=` | Mark overdue when past due |
| `POST .../writeoff` | → WRITTEN_OFF (+ ledger) |
| `POST .../payment` | Unified payment application path |
| `PATCH .../due-date` | Update due date |
| `PATCH ...` (draft) | Edit draft lines / fields |
| `GET .../versions` | Invoice version snapshots |
| `DELETE` | **405** — use write-off |

Scheduled overdue job: `overdue.marking.*` config.

### 5.3 Payments + allocation

1. `POST /api/v1/payments` — record RECEIVED payment (idempotent key supported).
2. `POST .../allocate/fifo` or `.../allocate/manual` — durable allocation + invoice amountPaid + events.
3. List/get + reverse/refund via payment resources/use cases.
4. Idempotency store is **DB-backed** (not process-local map).

### 5.4 Cheques

```
RECEIVED ──deposit──► DEPOSITED ──clear──► CLEARED
                         └──bounce──► BOUNCED
```

OCR: `/api/v1/cheques/ocr/parse` and upload; image OCR may be client-assisted (`web` Tesseract path).

### 5.5 Aging

| Endpoint | Behavior |
|----------|----------|
| `GET /api/v1/aging?asOfDate=` | **Live** report via `AgingUseCase` / domain engine |
| `GET /api/v1/aging/buckets` | Bucket labels |
| `POST /api/v1/aging/discount/calculate` | 2% early-payment discount |

### 5.6 Credit notes

Create early-payment discount notes; apply against payment. Residual AR balance impact tracked in product stories.

### 5.7 Ledger

| Business event | Debit | Credit |
|----------------|-------|--------|
| Invoice issued | AR | REVENUE |
| Payment received | BANK | AR |
| Write-off | EXPENSE | AR |

Posts are **durable** on main application paths. Account codes today are domain enum-backed (chart-of-accounts seed / periods still residual — STORY-020).

### 5.8 Outbox, Kafka, webhooks

```
[Write side - same DB TX]
  DomainEvent → EventPublisher → KafkaEventPublisher → ar_outbox PENDING

[Scheduled]
  OutboxWorker → optional SmallRyeOutboxKafkaSender (if OUTBOX_KAFKA_ENABLED)
  WebhookDispatcher → HTTP POST to matching subscriptions (HMAC + retries)
```

Defaults: outbox worker **on**; Kafka emit **off**; webhook delivery **on**.

---

## 6. Multi-tenancy design

| Layer | Mechanism | Status |
|-------|-----------|--------|
| Transport | `X-Tenant-Id` (and JWT `tenant_id` when JWT mode) | Enforced |
| Auth | API-key maps to tenant; JWT claim | When security enabled |
| Application | `TenantContext` ThreadLocal + cleared after request | Enforced |
| Repositories | `WHERE tenant_id = ?` | Enforced |
| Database RLS | `app.current_tenant_id` via TenantFilter + **Agroal interceptor** | Postgres |
| H2 / local | GUC no-op; app filters still apply | Dev |
| Events / outbox | `tenantId` on every row/event | Enforced |

Never add `findById(id)` without tenant.

---

## 7. Data model & Flyway

**Source of truth for schema:** `ar-bootstrap/src/main/resources/db/migration/`

| Version | Purpose |
|---------|---------|
| V1 | Init AR schema |
| V2 | Idempotency table |
| V3 | JPA alignment + seed |
| V4 | JSONB/text compat |
| V5 | Audit IP varchar |
| V6 | Webhooks + indexes |
| V7 | Webhook delivery log |

Historical copies under `docs/sql/` are documentation only — prefer Flyway.

IDs: **UUID v7**. Money: **NUMERIC(19,2)** + `Money` VO. See [SCHEMA.md](./SCHEMA.md).

---

## 8. Configuration & runtime profiles

File: `ar-bootstrap/src/main/resources/application.yml`

| Profile | Database | Hibernate DDL | Flyway | Typical use |
|---------|----------|---------------|--------|-------------|
| **default** | PostgreSQL | `none` | migrate-at-start | Local Postgres / compose |
| **`%dev`** | H2 mem `jdbc:h2:mem:testdb` | `update` | off | Fast local API (preferred) |
| **`%test`** | H2 mem | `update` | off | Automated tests |
| **`%prod`** | Postgres (env) | `none` | on | Production; security forced on |

> **Release note (STORY-022):** The legacy `%sqlite` profile alias was **removed**. Use `dev` only for H2. It was never a SQLite file database.

### Security (`invoicegenie.security.*`)

| Key | Dev default | Prod expectation |
|-----|-------------|------------------|
| `enabled` | `false` | `true` (forced in `%prod` + validator) |
| `mode` | `api-key` | `api-key` or `jwt` |
| `api-keys` | `none` | `key:tenantUuid,...` |
| `jwt.secret` | `none` | ≥16 chars if jwt mode |
| `allow-openapi` | `true` | `false` |

`ProdSecurityValidator` **aborts `%prod` startup** if security is off, secrets missing, or datasource uses known demo passwords (`ar`/`ar`, etc.).

### Outbox / webhooks

| Key | Default |
|-----|---------|
| `outbox.enabled` | true |
| `outbox.kafka-enabled` (`OUTBOX_KAFKA_ENABLED`) | false |
| `webhook.delivery.enabled` | true |

### Docker

- `docker-compose.yml` — full stack; passwords required via `.env`
- `docker-compose.prod.yml` — TLS nginx edge, private Postgres, prod security defaults  
  See [deploy/PROD_EDGE_TLS.md](./deploy/PROD_EDGE_TLS.md).

### H2 packaged-jar limitation (DEF-BE-007)

The **packaged** Quarkus app (`Dockerfile.prod` / `quarkus-run.jar`) is built for the **default/prod Postgres** runtime. Setting `-Dquarkus.profile=dev` on a packaged jar **does not** reliably reconfigure to H2 the way `quarkus:dev` does (build-time datasource extensions / packaging boundaries).

| Goal | How |
|------|-----|
| Local H2 | `mvn -pl ar-bootstrap -Dquarkus.profile=dev quarkus:dev` or `scripts/dev-up.*` |
| Prod-like Postgres | compose / jar with Postgres env + Flyway |
| Do **not** expect | `java -jar quarkus-run.jar -Dquarkus.profile=dev` for H2 smoke |

### Required headers

| Header | Required | Purpose |
|--------|----------|---------|
| `X-Tenant-Id` | Yes (API-key mode / header tenancy) | Tenant UUID |
| `X-API-Key` or `Authorization: Bearer` | When security enabled | Auth |
| `Idempotency-Key` | Optional | Invoice create, payments, allocations |
| `Content-Type: application/json` | Body requests | JSON |

### Observability

- Health: `/q/health`
- Metrics: `/q/metrics` (Prometheus)
- OpenAPI/Swagger: `/q/openapi`, `/q/swagger-ui/` (disabled in `%prod`)
- Optional OTel: `QUARKUS_OTEL_ENABLED`

---

## 9. Testing strategy

| Module | Style |
|--------|-------|
| `shared-kernel`, `ar-domain` | Unit |
| `ar-application` | Unit + Mockito |
| adapters | Unit/contract |
| `ar-bootstrap` | `@QuarkusTest` E2E (H2) |
| `web/` | lint, `tsc`, Playwright smoke |

```bash
mvn test
mvn verify                    # includes JaCoCo 80% line floor
./scripts/coverage.sh         # or coverage.ps1
./scripts/test-api.sh http://localhost:8080
./scripts/security-scan.sh    # OWASP + npm audit
```

---

## 10. Local development quick reference

### Prerequisites

| Tool | Version | Required? |
|------|---------|-----------|
| JDK | 17+ | Yes |
| Maven | 3.9+ | Yes |
| Node.js | 20+ | Yes (web GUI) |
| Docker / Postgres 15+ | — | Optional (default/prod) |
| Kafka | — | No unless enabling Kafka emit |

### Run API (H2 — preferred)

```bash
mvn clean install -DskipTests
mvn -pl ar-bootstrap -Dquarkus.profile=dev -Dquarkus.kafka.devservices.enabled=false quarkus:dev
# or: ./scripts/dev-up.sh  |  ./scripts/dev-up.ps1
```

### Run web

```bash
cd web && cp .env.example .env.local && npm install && npm run dev
# http://localhost:3000  — Settings sets X-Tenant-Id
```

Default smoke tenant: `00000000-0000-0000-0000-000000000001`

### Run Postgres path

```bash
cp .env.example .env   # set strong POSTGRES_PASSWORD / API keys
docker compose up -d postgres
mvn -pl ar-bootstrap -Dquarkus.kafka.devservices.enabled=false quarkus:dev
```

### PowerShell note

```powershell
mvn -pl ar-bootstrap "-Dquarkus.profile=dev" "-Dquarkus.http.port=8082" quarkus:dev
```

---

## 11. Where to change what

| You want to… | Prefer… | Avoid… |
|--------------|---------|--------|
| Business rule | `ar-domain` | REST if-blocks |
| Orchestration | inbound port + service in `ar-application` | Logic only in Resource |
| HTTP surface | `ar-adapter-api` | Domain framework annotations |
| SQL/JPA | persistence entity/mapper/adapter + **new Flyway version** | Hand-editing prod DB only |
| Event delivery | `ar-adapter-messaging` | Kafka calls from application services |
| CDI wiring | `ArApplication` / producers | Cyclic adapter deps |
| Operator UI | `web/` | Hardcoding secrets in client bundles |

---

## 12. Known gaps & residual debt (2026-07-24)

Honest residuals — prefer stories/backlog for status:

1. **OIDC / web SSO** — Phase-1 API-key + JWT roles landed; full IdP login open (STORY-003 residual, STORY-021).
2. **Edge TLS** — ops-owned; sample nginx + `docker-compose.prod.yml` (STORY-017).
3. **Credit note AR impact / collections** — product deepening (STORY-007 residual, STORY-015).
4. **Chart of accounts as data + period close** — enum accounts today (STORY-020).
5. **Kafka/gRPC inbound consumers** — publish path only (P2-08 deferred).
6. **Cheque OCR** — heuristic + client image path (STORY-018).
7. **Concurrent allocation hardening** — optimistic locks residual (STORY-019).
8. **Packaged jar ≠ H2** — DEF-BE-007 (see §8).

**Closed vs older onboarding myths:** ledger is **not** in-memory; aging report is **not** stubbed; draft create **exists**; idempotency is **DB-backed**; RLS GUC **is** set (filter + Agroal); messaging is **not** fully stubbed (Kafka sender + webhook delivery exist); web UI **exists**; Flyway **V1–V7** is active; Quarkus is **3.27.3 LTS**.

---

## 13. Glossary

| Term | Meaning here |
|------|----------------|
| Aggregate | Consistency boundary (e.g. Invoice + lines) |
| Port / Adapter | Hexagonal dependency inversion |
| Tenant | Isolated org; all data partitioned by `tenant_id` |
| Outbox | Same-TX event store for reliable publish |
| FIFO allocation | Oldest open invoices first |
| UUID v7 | Time-ordered ids |
| RLS | Postgres row-level security |

---

## 14. Suggested first week

1. Read this doc + skim [SCHEMA.md](./SCHEMA.md) + residual table in [FEATURE_PRIORITY_BACKLOG.md](./FEATURE_PRIORITY_BACKLOG.md).
2. Run H2 + web; create customer → invoice → payment → allocate; open Swagger.
3. Debugger trace: filters → `InvoiceResource` → `IssueInvoiceService` → domain → JPA → outbox.
4. Read `InvoiceLifecycleEngine` + domain tests.
5. `mvn -pl ar-domain,ar-application test`.
6. Pick a story from [PRODUCT_OWNER_STORIES.md](./PRODUCT_OWNER_STORIES.md) with **Status: Ready**.

---

## 15. Document maintenance

| When you change… | Update… |
|------------------|---------|
| Module boundaries | §3–4 |
| HTTP surface | §5 + README |
| Schema | Flyway + SCHEMA.md + §7 |
| Profiles / security | §8 + README + PRODUCTION_READINESS |
| Maturity / stubs closed | §12 + PRODUCT_OWNER_STORIES + FEATURE_PRIORITY_BACKLOG |

---

*Onboarding blueprint aligned to codebase 2026-07-24. Complements README (ops/API) and product-owner stories (what to build next).*