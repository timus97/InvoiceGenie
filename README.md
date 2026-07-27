# InvoiceGenie

### The accounts receivable platform that bills, collects, and reminds — so cash comes in on time.

InvoiceGenie is a **multi-tenant Accounts Receivable (AR) product** for finance teams and SaaS operators who need more than a spreadsheet and less than a bloated ERP. Issue invoices, apply payments, track aging, post a balanced ledger — and **notify customers by Email and WhatsApp** when money is due.

---

## Why InvoiceGenie?

| Pain today | With InvoiceGenie |
|------------|-------------------|
| Spreadsheets break under multi-company ops | **True multi-tenant isolation** (app + Postgres RLS) |
| Invoices issued but customers never hear about them | **Automated Email + WhatsApp** on issue, pre-due, and dunning |
| Partial payments and cheques are manual chaos | **FIFO / manual allocation**, cheque lifecycle, credit notes |
| “Does the AR balance?” is a late-night question | **Double-entry ledger** on issue, pay, write-off |
| Audit asks for who changed what | **Audit trail**, roles, JWT sessions, refresh-token security |
| Integrations are one-off scripts | **REST API**, webhooks, transactional outbox |

**Bottom line:** InvoiceGenie is the receivable backbone for teams that need **control, collections outreach, and books that balance**.

---

## Who it’s for

| Audience | Value |
|----------|--------|
| **AR / Collections teams** | Day-to-day invoices, payments, reminders, customer contact history |
| **Controllers** | Write-offs, reversals, policy, dunning, notification policy |
| **Tenant admins** | Users, roles, notification channels, webhooks |
| **Auditors** | Read-only aging, audit export, delivery history |
| **Platform / SaaS** | One product, many customer orgs, no data cross-leak |
| **Integrators** | OpenAPI, webhooks, Kafka-ready outbox |

---

## Domain: Accounts Receivable done right

InvoiceGenie models the full AR domain with enforceable rules:

```
Customer ──► Invoice (DRAFT → ISSUED → PARTIALLY_PAID → PAID)
                  │              ↘ OVERDUE → WRITTEN_OFF
                  ├── Payments & allocations (FIFO / manual)
                  ├── Cheques (receive → deposit → clear / bounce)
                  ├── Credit notes & early-pay discount
                  ├── Aging buckets (0–30 … 90+)
                  ├── Ledger journals (AR / Revenue / Bank / Expense)
                  └── Customer notifications (Email / WhatsApp)
```

**Multi-tenant by design:** every business row is scoped by `tenant_id`. Application filters + optional Postgres RLS = defence in depth.

---

## Feature list (product)

### Core AR
- **Customers** — master data, credit limits, block/unblock  
- **Invoices** — lifecycle, line items, versions, issue & write-off  
- **Payments** — record, allocate, reverse, unallocate  
- **Cheques** — deposit, clear (creates payment), bounce  
- **Aging** — live open AR by bucket + early-pay discount calculator  
- **Credit notes** — document and apply adjustments  
- **Ledger** — automatic double-entry on key events  
- **Statements** — open-item style reporting  
- **FX rates** — multi-currency support  
- **Tenants** — multi-company registry  

### Collections & customer messaging *(new)*
- **Send invoice** — automatic on issue + manual from UI  
- **Payment reminders** — pre-due (configurable days, catch-up window)  
- **Dunning notices** — aligned to 30 / 60 / 90 day policy, de-duplicated per level  
- **Email channel** — logging (demo) or SMTP (pluggable)  
- **WhatsApp channel** — template-ready (logging demo; Meta Cloud API hook)  
- **Preferences & opt-out** — per customer, per channel  
- **Tenant notification policy** — channels, auto-on-issue, reminder days  
- **Delivery history** — status, skip reasons, attempt log  
- **Idempotent enqueue** — no double-spam on job re-runs  
- **Secure ops** — PII redacted logs, rate-limited manual send, policy not bypassable by clerks  

### Security & platform
- **Email + password login** (BCrypt), **JWT access (15 min)** + **refresh tokens (7 days)** with rotation & revocation  
- **RBAC** — AR_CLERK, AR_CONTROLLER, AR_AUDITOR, TENANT_ADMIN  
- **Admin user management** UI  
- **BFF session** — tokens never exposed in browser storage or API response bodies  
- **Webhooks** — HMAC-signed outbound to tenant systems  
- **Audit log** — exportable  
- **Idempotency keys** on critical posts  

### Operator console (Next.js)
Dashboard · Customers · Invoices · Payments · Cheques · Aging · Credit notes · Ledger · **Notifications** · Users · Tenants · Webhooks · Audit · Settings  

---

## Business outcomes

1. **Faster cash collection** — customers get the invoice and timely reminders without manual chasing.  
2. **Lower operational risk** — state machines and ledger posts reduce “we forgot to reverse that” moments.  
3. **Audit-ready** — who sent what, who changed policy, who wrote off AR.  
4. **Scale multi-company** — SaaS and shared-services finance teams isolate tenants cleanly.  
5. **Integration-ready** — REST + webhooks + outbox for ERP and banking connectors.  

---

## Architecture (at a glance)

| Layer | Technology |
|-------|------------|
| API & domain | Java 17, Quarkus 3.27, hexagonal modules |
| Data | PostgreSQL 15, Flyway, RLS |
| Messaging | Outbox, scheduled jobs, webhook + notification dispatchers |
| Console | Next.js 15, React Query, BFF with httpOnly sessions |
| Ops | Docker Compose, Adminer, health/metrics, AWS IaC docs |

Hexagonal modules: `ar-domain` · `ar-application` · `ar-adapter-api` · `ar-adapter-persistence` · `ar-adapter-messaging` · `ar-bootstrap` · `web/`

---

## Quick start (local)

```bash
# 1) Database + SQL console
docker compose up -d postgres adminer

# 2) Backend (PostgreSQL — set password from .env)
#    JDBC: jdbc:postgresql://localhost:5432/invoicegenie
#    user/password: ar / <POSTGRES_PASSWORD from .env>
mvn -pl ar-bootstrap -am quarkus:dev -Dquarkus.profile=dev -Dquarkus.http.port=8082

# 3) Frontend
cd web && npm install && npm run dev
```

| URL | Purpose |
|-----|---------|
| http://localhost:3000 | Operator console |
| http://localhost:8082 | REST API |
| http://localhost:8082/q/swagger-ui/ | OpenAPI |
| http://localhost:8081 | Adminer (SQL UI) |

**Bootstrap admin:** `admin@invoicegenie.local` / `Admin123!`  
**Demo tenant:** `00000000-0000-0000-0000-000000000001`

> Change the bootstrap password after first login in any shared environment.

---

## Customer notifications — how it works

1. **Issue invoice** → outbox event → enqueue Email/WhatsApp (policy + prefs).  
2. **Dispatch worker** claims rows safely (`FOR UPDATE SKIP LOCKED`) and delivers (logging by default).  
3. **Reminders & dunning** run on schedule; one message per invoice/channel/level.  
4. **History** in **Notifications** UI; **opt-out** on customer; **policy** in Settings (admin).  

Docs: start at [`docs/README.md`](docs/README.md) · demo [`docs/DEMO.md`](docs/DEMO.md) · status [`docs/PROJECT_STATUS.md`](docs/PROJECT_STATUS.md).

---

## Security highlights

- Production-style **JWT + refresh rotation** stored hashed in Postgres  
- **Roles** enforced on API and UI  
- Notification **PII redacted** in default logs  
- Clerks **cannot force** past tenant-disabled notification policy  
- **Rate-limited** manual notification send  

---

## Roadmap-friendly

Built so **Accounts Payable (AP)** and **General Ledger (GL)** can share the same tenant, ledger, and security foundation.

---

## License & contact

Internal product / evaluation build. See `docs/PROJECT_STATUS.md`, `docs/PO_NEXT_REVIEW.md`, and your engagement owner.

**InvoiceGenie — AR that collects, not just records.**