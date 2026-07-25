# InvoiceGenie

**Multi-tenant Accounts Receivable (AR) platform** — issue invoices, collect payments, track aging, and keep books that balance.

InvoiceGenie is a production-oriented AR product: a **Java/Quarkus backend** with domain-driven design and strict tenant isolation, plus a **Next.js operator console**. It is built to be the receivable backbone for SaaS and finance teams, and designed so future **Accounts Payable (AP)** and **General Ledger (GL)** modules can plug into the same foundation.

---

## What problem does it solve?

Every business that bills customers needs a reliable answer to four questions:

1. **Who owes us money?** — customers, open invoices, credit exposure  
2. **What have we collected?** — cash, bank transfers, cheques, allocations  
3. **Is our AR aging healthy?** — current vs overdue buckets, early-pay discounts  
4. **Do the books balance?** — double-entry ledger that mirrors real cash application  

Spreadsheets and ad-hoc tools break down under multi-company (multi-tenant) operations, concurrent payments, partial allocations, bounced cheques, write-offs, and audit requirements. InvoiceGenie turns those flows into **enforced business rules**, **auditable state machines**, and **APIs** that operators and integrations can trust.

### Who it is for

| Audience | How they use InvoiceGenie |
|----------|---------------------------|
| **Finance / AR teams** | Day-to-day invoice, payment, cheque, and aging work in the web console |
| **Controllers / auditors** | Write-offs, reverse/refund paths, ledger balances, audit export |
| **Platform / SaaS operators** | Multi-tenant isolation so each customer org only sees its own data |
| **Integrators** | REST API, webhooks, optional Kafka outbox for ERP or banking systems |
| **Engineers** | Hexagonal modules, Flyway schema, Docker/prod compose, AWS IaC docs |

---

## What it does (product capabilities)

| Capability | Business value |
|------------|----------------|
| **Customers** | Master data, credit limits, block/unblock so you do not invoice bad risk |
| **Invoices** | Full lifecycle from draft → issued → partially paid → paid, plus overdue and write-off |
| **Payments** | Record cash receipts; allocate to one or many invoices (FIFO or manual) |
| **Cheques** | Receive → deposit → clear or bounce, with real payment linkage on clear |
| **Aging** | Live AR aging (0–30, 31–60, 61–90, 90+) and early-payment discount (2%) |
| **Credit notes** | Document discounts and adjustments; apply against payments |
| **Ledger** | Automatic double-entry journals (AR, Revenue, Bank, Expense) on key events |
| **Multi-tenancy** | Every row scoped by tenant; app filters + Postgres RLS defence-in-depth |
| **Auth & roles** | API keys, JWT login, roles (clerk, controller, auditor, tenant admin) |
| **Webhooks & outbox** | Notify external systems; transactional outbox with optional Kafka publish |
| **Audit** | Before/after trails and CSV export for compliance |
| **Web console** | Next.js UI for dashboard, customers, invoices, payments, cheques, aging, ledger, settings |

### Core business workflows

**Invoice lifecycle**

```
DRAFT → ISSUED → PARTIALLY_PAID → PAID
                ↓
              OVERDUE → WRITTEN_OFF
```

- Issue only when the customer is invoiceable (not blocked/deleted; credit limit enforced).  
- Terminal states: **PAID**, **WRITTEN_OFF**.  
- Version snapshots keep an immutable audit trail of invoice changes.

**Payment application**

- One payment can cover many invoices; one invoice can take many partial payments.  
- **FIFO** auto-allocation (oldest open invoices first) or **manual** line-level amounts.  
- Idempotency keys prevent double-posting; optimistic locking protects concurrent allocate/reverse.

**Cheque cash application**

```
RECEIVED → DEPOSITED → CLEARED  (creates payment + allocations)
                    ↘ BOUNCED   (reverses application when cleared)
```

**Ledger posts (examples)**

| Event | Debit | Credit |
|-------|-------|--------|
| Invoice issued | AR | Revenue |
| Payment received | Bank | AR |
| Write-off | Expense | AR |

---

## Architecture at a glance

Hexagonal / clean architecture with a pure domain core:

```
  Browser (web/)  or  API client
           │  X-Tenant-Id  (+ Bearer / API key when security is on)
           ▼
  ar-adapter-api          REST, auth, tenant & role filters
           ▼
  ar-application          Use cases (issue invoice, allocate, clear cheque, …)
           ▼
  ar-domain               Aggregates & business rules (no framework deps)
           │
     ┌─────┴──────┬────────────────┐
     ▼            ▼                ▼
 persistence   messaging      shared-kernel
 (JPA/Flyway)  (outbox/       (TenantId, Money,
  + RLS)        Kafka/webhooks) ActorContext)
           │
           ▼
     ar-bootstrap             Runnable Quarkus app
```

| Module | Role |
|--------|------|
| `shared-kernel` | Cross-cutting value objects (`TenantId`, `Money`, …) |
| `ar-domain` | Pure AR domain — zero infrastructure dependencies |
| `ar-application` | Use cases and ports |
| `ar-adapter-api` | REST + security filters |
| `ar-adapter-persistence` | JPA, repositories, Flyway-backed schema usage |
| `ar-adapter-messaging` | Transactional outbox, Kafka sender, webhooks |
| `ar-bootstrap` | Composition root, config, migrations |
| `web/` | Next.js 15 AR console (proxies `/api/*` to Quarkus) |

**Tech stack:** Java 17 · Quarkus 3.27 LTS · Maven multi-module · PostgreSQL 15+ (H2 for local `dev`) · Flyway · Next.js 15 · Docker Compose · optional Kafka · Terraform/CloudFormation docs under `docs/aws/`

---

## Prerequisites

| Tool | Version | Required? | Purpose |
|------|---------|-----------|---------|
| **JDK** | 17+ | Yes | Compile & run Quarkus |
| **Maven** | 3.9+ | Yes | Multi-module build |
| **Node.js** | 20+ | Yes (GUI) | Next.js console in `web/` |
| **Docker Desktop** (or Postgres 15+) | Recent | Optional | Postgres / full stack |
| **curl** or **Postman** | — | Optional | API smoke tests |

```bash
java -version   # 17+
mvn -version    # same JDK (JAVA_HOME)
```

**Windows:** quote `-D` properties in PowerShell, e.g.  
`mvn -pl ar-bootstrap "-Dquarkus.profile=dev" quarkus:dev`

---

## Quick start

### Fastest path — H2 in-memory (no Postgres)

Profile **`dev`** uses ephemeral H2 on port **8080**:

```bash
mvn clean install -DskipTests
mvn -pl ar-bootstrap -Dquarkus.profile=dev -Dquarkus.kafka.devservices.enabled=false quarkus:dev
```

Helpers: `./scripts/dev-up.sh` or `./scripts/dev-up.ps1`

### Postgres (production-like)

```bash
docker compose up -d postgres
mvn clean install -DskipTests
mvn -pl ar-bootstrap -Dquarkus.kafka.devservices.enabled=false quarkus:dev
```

Default JDBC: `jdbc:postgresql://localhost:5432/invoicegenie` · user/pass `ar`/`ar`. Flyway applies schema on start for Postgres/prod.

### Full Docker stack

```bash
cp .env.example .env   # set POSTGRES_PASSWORD and INVOICEGENIE_API_KEYS
docker compose up -d --build
```

| URL | Description |
|-----|-------------|
| http://localhost:3000 | Web console |
| http://localhost:8080 | REST API |
| http://localhost:8080/q/swagger-ui/ | Swagger UI |
| http://localhost:8080/q/health | Health |

**Production-hardened stack** (TLS edge nginx, private DB):  
`docker compose -f docker-compose.prod.yml up -d --build` — see [docs/deploy/PROD_EDGE_TLS.md](docs/deploy/PROD_EDGE_TLS.md).

### Web GUI (Next.js)

```bash
# Terminal 1 — API
mvn -pl ar-bootstrap "-Dquarkus.profile=dev" "-Dquarkus.kafka.devservices.enabled=false" quarkus:dev

# Terminal 2 — UI
cd web
cp .env.example .env.local
npm install
npm run dev
```

Open http://localhost:3000 — login at `/login` (username/password JWT or API key session).  
Default smoke tenant: `00000000-0000-0000-0000-000000000001`  
In production set `NEXT_PUBLIC_ALLOW_TENANT_OVERRIDE=false` so tenant comes only from login.

---

## Security (summary)

| Variable | Purpose |
|----------|---------|
| `INVOICEGENIE_SECURITY_ENABLED` | `true` in `%prod` (fail-closed if misconfigured) |
| `INVOICEGENIE_SECURITY_MODE` | `api-key` \| `jwt` \| `hybrid` |
| `INVOICEGENIE_API_KEYS` | `key:tenantUuid,...` for M2M |
| `INVOICEGENIE_JWT_SECRET` | HS256 secret (≥16 chars in prod) |
| `INVOICEGENIE_SECURITY_USERS` | `user:pass:tenant:ROLE1\|ROLE2,...` for web login |
| `NEXT_PUBLIC_ALLOW_TENANT_OVERRIDE` | **`false` in prod** |

Roles (resource-level): `AR_CLERK`, `AR_CONTROLLER`, `AR_AUDITOR`, `TENANT_ADMIN`.  
Login: `POST /api/v1/auth/login` with `{username,password}` or `{apiKey}`.

---

## API surface (high level)

All requests need `X-Tenant-Id` (and credentials when security is enabled). Optional `Idempotency-Key` on POSTs.

| Area | Examples |
|------|----------|
| **Auth** | `POST /api/v1/auth/login` |
| **Customers** | CRUD, block/unblock, credit-check |
| **Invoices** | create/issue, list/get, payment, overdue, write-off, due-date |
| **Payments** | create, list/get, allocate FIFO/manual, reverse/refund |
| **Cheques** | create, deposit, clear, bounce |
| **Aging** | report, buckets, early-pay discount |
| **Credit notes** | create, apply |
| **Ledger** | accounts, balance, by transaction/reference |
| **Webhooks / tenants / audit** | subscriptions, tenant registry, export |

**Postman:** [postman/InvoiceGenie-AR.postman_collection.json](postman/InvoiceGenie-AR.postman_collection.json)  
**OpenAPI:** http://localhost:8080/q/openapi when the server is running.

### Quick smoke

```bash
curl -s http://localhost:8080/q/health

curl -s -X POST http://localhost:8080/api/v1/customers \
  -H "Content-Type: application/json" \
  -H "X-Tenant-Id: 00000000-0000-0000-0000-000000000001" \
  -d '{"customerCode":"C1","legalName":"Acme","currency":"USD"}'

curl -s -X POST http://localhost:8080/api/v1/invoices \
  -H "Content-Type: application/json" \
  -H "X-Tenant-Id: 00000000-0000-0000-0000-000000000001" \
  -d '{"invoiceNumber":"INV-1","customerId":"<customer-uuid>","currencyCode":"USD","dueDate":"2026-12-31","lines":[{"description":"Svc","amount":100}]}'
```

---

## Multi-tenancy & data model

- **Single database**, every table has `tenant_id`; repositories always take `TenantId` first.  
- **Postgres RLS** + app filters so tenant data does not leak across orgs.  
- **IDs:** UUID v7 (time-ordered). **Money:** `NUMERIC(19,2)`, ISO 4217 currencies.  
- **Core tables:** tenants, customers, invoices (+ lines/versions), payments (+ allocations), cheques, ledger entries, outbox, audit log, idempotency, webhooks, exchange rates.

Full schema commentary: [docs/SCHEMA.md](docs/SCHEMA.md).

---

## Testing & quality

```bash
mvn test                              # unit tests (all modules)
./scripts/coverage.ps1                # coverage + 80% line floor
./scripts/test-api.ps1 -BaseUrl http://localhost:8080
./scripts/security-scan.ps1           # OWASP Dependency-Check + npm audit
```

CI enforces tests, coverage gates, and dependency scanning. See [docs/PRODUCTION_READINESS.md](docs/PRODUCTION_READINESS.md).

---

## Messaging

| Path | Default | Enable |
|------|---------|--------|
| Transactional outbox (DB) | On | Always written with domain events |
| Kafka topic publish | Off | `OUTBOX_KAFKA_ENABLED=true` |
| Customer HTTP webhooks | On | `WEBHOOK_DELIVERY_ENABLED` (HMAC, SSRF checks, retries) |

---

## Documentation map

| Doc | Purpose |
|-----|---------|
| [docs/ONBOARDING.md](docs/ONBOARDING.md) | Architecture blueprint, module map, local runbook |
| [docs/PRODUCT_OWNER_STORIES.md](docs/PRODUCT_OWNER_STORIES.md) | Ordered product stories & acceptance criteria |
| [docs/FEATURE_PRIORITY_BACKLOG.md](docs/FEATURE_PRIORITY_BACKLOG.md) | Residual backlog & capability maturity |
| [docs/SCHEMA.md](docs/SCHEMA.md) | Data model & design decisions |
| [docs/PRODUCTION_READINESS.md](docs/PRODUCTION_READINESS.md) | Production checklist |
| [docs/deploy/PROD_EDGE_TLS.md](docs/deploy/PROD_EDGE_TLS.md) | Edge TLS & prod compose |
| [docs/aws/](docs/aws/) | AWS architecture, Terraform, CloudFormation |
| [docs/diagrams/](docs/diagrams/) | Architecture & flow diagrams |
| [web/README.md](web/README.md) | Web console setup |

---

## Roadmap (product extensions)

| Module | Intent |
|--------|--------|
| **AP (Payables)** | Vendor bills and outbound payments — separate bounded context |
| **GL (General Ledger)** | Consume AR/AP events; trial balance & full chart of accounts |
| **Dunning** | Overdue reminders and escalation workflows |
| **Enterprise SSO** | OIDC (Keycloak/Cognito) beyond built-in JWT/API-key |
| **Reporting** | Cash forecast, revenue recognition, richer analytics |

---

## License

Proprietary — InvoiceGenie project.
