# InvoiceGenie — Project Review for Product Owner & Business Stakeholders

**Document type:** Project status & production roadmap  
**Audience:** Product Owner, business stakeholders, eng leadership  
**Prepared by:** Engineering Team Lead  
**Review date:** 2026-07-27  
**Branch / baseline:** `Development` (post Wave A–D + Notifications MVP)  
**Classification:** Internal — decision support for production path  

---

## 1. Executive summary (2-minute read)

InvoiceGenie is a **multi-tenant Accounts Receivable (AR) platform**: issue invoices, apply payments (including cheques), age open AR, post a double-entry subledger, audit who did what, and **proactively notify customers** (Email + WhatsApp pipeline) when money is due.

### Where we are today

| Dimension | Assessment |
|-----------|------------|
| **Product maturity** | **Core AR product is real and largely end-to-end** — not a prototype |
| **Business demo / internal pilot** | **Ready** (logging email provider; WhatsApp off by default) |
| **External customer email / WhatsApp** | **Not ready** until real SMTP/SES and Meta Cloud API clients ship |
| **Production SaaS go-live** | **Conditional** — platform foundations exist; production channels, SSO, load/ops hardening still open |
| **Engineering health** | Strong domain model, hexagonal modules, Flyway, CI, RBAC, ~647 unit tests green on last local Surefire aggregate |

### Headline numbers

| Metric | Value |
|--------|------:|
| Backend Java modules | 7 (`shared-kernel` → `ar-bootstrap`) |
| Backend source files (main) | ~263 |
| Backend unit test classes | ~112 |
| REST resource areas | 17 |
| Operator console pages | 15+ |
| Flyway migrations | V1–V12 |
| Core AR product stories (STORY-001…022 + QA) | **All Done** for defined MVP scope |
| Notification P0 stories | **Implemented** (logging adapters; High QA findings fixed) |
| Open production blockers (real delivery) | Real SMTP/SES, Meta WhatsApp, enterprise SSO (optional), cloud TLS/ops ownership |

### Recommendation to stakeholders

1. **Treat the product as pilot-ready for internal finance ops** with demo notification sink.
2. **Fund a Production Path program of 4–6 sprints** (see §11) to wire real messaging providers, close residual QA/ops gaps, restore coverage gates, and run a controlled pilot.
3. **Defer AP / full GL / multi-region** until AR pilot proves cash-collection value.
4. **Recommended team for production path:** 5–7 people (see §12).

---

## 2. Product vision & positioning

### What InvoiceGenie is

> The accounts receivable platform that **bills, collects, and reminds** — so cash comes in on time.

It sits between spreadsheets and a full ERP: true multi-tenant isolation, enforceable AR state machines, cash application, collections outreach, and books that balance on the AR subledger.

### Who it serves

| Audience | Value delivered today |
|----------|----------------------|
| **AR / Collections** | Invoices, payments, aging, reminders, notification history |
| **Controllers** | Write-offs, reverse/unallocate, credit notes, dunning policy, ledger |
| **Tenant admins** | Users/roles, notification policy, webhooks, tenants |
| **Auditors** | Read paths, audit CSV export, delivery attempt history |
| **Integrators** | REST + OpenAPI, HMAC webhooks, transactional outbox (Kafka optional) |
| **Platform / SaaS** | Tenant isolation (app filter + Postgres RLS) |

### Competitive positioning

| Pain today | InvoiceGenie answer |
|------------|---------------------|
| Spreadsheets break under multi-company ops | Multi-tenant isolation |
| Invoices issued but customers never hear | Email + WhatsApp notification pipeline |
| Partial payments / cheques are chaos | FIFO/manual allocation + cheque lifecycle |
| "Does AR balance?" is a late-night question | Double-entry ledger on issue/pay/write-off |
| Audit asks who changed what | Audit trail + RBAC + actor/IP/UA |
| Integrations are one-off scripts | REST, webhooks, outbox |

---

## 3. Architecture at a glance

| Layer | Technology |
|-------|------------|
| API & domain | Java 17, **Quarkus 3.27.3 LTS**, hexagonal DDD modules |
| Data | PostgreSQL 15, **Flyway V1–V12**, optional RLS |
| Messaging / jobs | Transactional outbox, webhook dispatcher, notification dispatch, dunning/reminder/overdue jobs |
| Console | **Next.js 15**, React Query, BFF with httpOnly session cookies |
| Ops | Docker Compose (dev + prod-like), Adminer, health/metrics, AWS Terraform/CFN docs |
| Quality | Maven multi-module, JaCoCo (target 80%), GitHub Actions CI + security workflow, Playwright smoke |

### Module map

```
shared-kernel          → shared primitives (ActorContext, etc.)
ar-domain              → aggregates, domain services, ports
ar-application         → use cases / application services
ar-adapter-api         → REST, auth filters, RBAC
ar-adapter-persistence → JPA / Panache / RLS helpers
ar-adapter-messaging   → outbox, webhooks, notifications, jobs
ar-bootstrap           → Quarkus app, Flyway, config
web/                   → Operator console (BFF)
```

### Local runtime (dev)

| Service | URL |
|---------|-----|
| Operator console | http://localhost:3000 |
| REST API | http://localhost:8082 |
| Swagger / OpenAPI | http://localhost:8082/q/swagger-ui/ |
| Adminer | http://localhost:8081 |
| Bootstrap admin | `admin@invoicegenie.local` / `Admin123!` |
| Demo tenant | `00000000-0000-0000-0000-000000000001` |


---

## 4. What has been built — features inventory

### 4.1 Feature inventory by product area

Legend: **Done** = production-shaped in code for pilot · **Partial** = works with known limits · **Stub** = interface/path exists, not real external effect · **Deferred** = intentional backlog

| # | Feature / capability | Status | Maturity | Notes |
|---|---------------------|--------|----------|-------|
| 1 | **Multi-tenant registry** (CRUD, activate/suspend) | Done | High | UI + API |
| 2 | **Customers** (CRUD, block/unblock, credit limit) | Done | High | Credit enforced on issue |
| 3 | **Customer AR summary** | Done | High | System-calculated open AR |
| 4 | **Invoices lifecycle** (DRAFT→ISSUED→PARTIALLY_PAID→PAID / OVERDUE / WRITTEN_OFF) | Done | High | Draft create + draft PATCH |
| 5 | **Invoice line richness** (qty, unit, discount, tax) | Done | High | Server computes totals |
| 6 | **Invoice version snapshots** | Done | High | History on create/lifecycle |
| 7 | **Credit limit & blocked-customer enforcement** | Done | High | 409 `CUSTOMER_NOT_INVOICEABLE` |
| 8 | **Payments** (record, list, get) | Done | High | Filters; cursor pagination light |
| 9 | **Payment allocation** (FIFO + manual) | Done | High | Same-currency only (policy) |
| 10 | **Payment reverse / refund** | Done | High | Controller roles |
| 11 | **Payment unallocate / reallocate** | Done | High | Controlled reverse of allocations |
| 12 | **Allocation concurrency integrity** | Done | High | Optimistic lock + reconciliation job |
| 13 | **Cheques** (receive→deposit→clear/bounce) | Done | High | Clear creates payment + allocates |
| 14 | **Cheque OCR** | Partial | Medium | PDF server-side; images via client Tesseract |
| 15 | **Credit notes** (EPD + apply → AR impact) | Done | High | Ledger Dr REVENUE / Cr AR |
| 16 | **Aging report** (buckets + discount calc) | Done | High | Dashboard widgets aligned |
| 17 | **Overdue marking job** | Done | High | Scheduled + audit |
| 18 | **Customer statements** (JSON/CSV) | Done | Medium | PDF deferred |
| 19 | **Dunning foundation** (levels 30/60/90) | Done | Medium | Events + notification bridge |
| 20 | **Double-entry ledger** (issue/pay/write-off/credit note) | Done | High | Chart-of-accounts seed MVP |
| 21 | **Period close / full GL** | Deferred | — | Intentionally out of AR MVP |
| 22 | **Exchange rates / FX utility** | Done | Medium | Conversion UI; no cross-currency settlement |
| 23 | **Cross-currency cash application** | Deferred | — | Phase-2 (FX gain/loss) |
| 24 | **AuthN** (email/password, BCrypt, JWT 15m + refresh 7d) | Done | High | Refresh rotation & revocation |
| 25 | **RBAC** (CLERK / CONTROLLER / AUDITOR / TENANT_ADMIN) | Done | High | API + role-aware UI |
| 26 | **User admin** | Done | High | Users page |
| 27 | **BFF session security** | Done | High | httpOnly cookies; tokens not in localStorage |
| 28 | **OIDC / enterprise SSO** | Deferred | — | Lightweight login only |
| 29 | **Webhooks** (subscribe + HMAC delivery + retries + SSRF) | Done | High | Delivery log UI/API |
| 30 | **Transactional outbox** (+ optional Kafka) | Done | Medium | Kafka env-gated |
| 31 | **Audit log + CSV export** | Done | High | Actor / IP / UA |
| 32 | **Idempotency keys** on critical writes | Done | High | DB store + TTL cleanup |
| 33 | **Notifications — domain, prefs, policy, templates** | Done | High | V11/V12 |
| 34 | **Send invoice on issue (auto + manual)** | Done | High | Outbox bridge |
| 35 | **Pre-due payment reminders** | Done | High | Catch-up window after QA fix |
| 36 | **Dunning notices to customer channels** | Done | High | De-dupe per level/channel |
| 37 | **Email channel** | Partial / Stub | Medium | **Logging default; SMTP fail-closed until real client** |
| 38 | **WhatsApp channel** | Partial / Stub | Medium | **Logging / fail-closed Meta; templates seeded** |
| 39 | **Notification history + attempts UI** | Done | High | Filters post-QA |
| 40 | **PII redaction, rate limit, SKIP LOCKED claim** | Done | High | High QA findings closed |
| 41 | **Operator console** (15+ pages) | Done | High | Dashboard through Settings |
| 42 | **Playwright E2E smoke** | Done | Medium | Critical paths only |
| 43 | **Prod Dockerfile + prod compose + edge TLS docs** | Done | Medium | Cloud certs ops-owned |
| 44 | **AWS hosting design (Terraform/CFN)** | Partial | Medium | IaC present; not live-deployed from this review |
| 45 | **Accounts Payable (AP) module** | Deferred | — | Future product |
| 46 | **Full General Ledger product** | Deferred | — | Beyond AR subledger |
| 47 | **Bounce/delivery provider webhooks** | Pending | — | Notification P1 |
| 48 | **Quiet hours, unsubscribe links, i18n, PDF attach** | Pending | — | Notification polish |

**Rough count for stakeholders**

| Category | Count (approx.) |
|----------|----------------:|
| Distinct product capabilities tracked above | **48** |
| Fully Done for pilot | **~38** |
| Partial / stub (usable with limits) | **~5** |
| Deferred / pending product expansion | **~5+** |
| Major end-to-end workflows live | **10** (see §5) |

---

## 5. Core workflows (what finance can actually run)

These are the **business workflows** already implemented end-to-end in API + UI (unless noted).

### WF-1 — Customer onboarding & credit control
1. Create/update customer (email, phone, credit limit)
2. Block / unblock for collections discipline
3. Live AR summary + credit check (system open balance)
4. Notification preferences (opt-out per channel)

### WF-2 — Invoice issue
1. Create DRAFT (optional) with rich lines (qty/tax/discount)
2. Edit draft via PATCH
3. Issue → lifecycle + ledger Dr AR / Cr Revenue
4. Credit/block rules enforced
5. Version snapshot
6. Outbox → auto enqueue **INVOICE_ISSUED** notification (policy permitting)

### WF-3 — Cash application (bank / transfer)
1. Record payment
2. Allocate FIFO or manual (same currency)
3. Invoice status → PARTIALLY_PAID / PAID
4. Ledger cash application
5. List/search payments; reverse/refund; unallocate (controller)

### WF-4 — Cheque collections
1. Register cheque (manual or OCR-assisted)
2. Deposit → Clear (creates CHECK payment + allocation) → Bounce with reverse
3. Confidence gate on OCR bulk create

### WF-5 — Credit notes & early-pay discount
1. Generate / create credit note
2. Apply → reduces AR + ledger
3. Available credits surfaced in payment UI

### WF-6 — Collections spine (aging + overdue + dunning)
1. Live aging buckets (0–30 … 90+)
2. Scheduled overdue marking
3. Dunning job / manual run → `DunningNotice`
4. Notification dispatch for dunning levels
5. Customer statement JSON/CSV

### WF-7 — Customer messaging
1. Policy (tenant admin): channels, auto-on-issue, pre-due days
2. Templates with `{{var}}` rendering
3. Dispatch worker with retries + SKIP LOCKED
4. History + attempt log; rate-limited manual send

### WF-8 — Integrations
1. Webhook subscription CRUD
2. HMAC-signed delivery, retries, SSRF protection, delivery log
3. Optional Kafka outbox publish

### WF-9 — Security & admin
1. Login → JWT + refresh rotation
2. Roles gate sensitive actions (write-off, reverse, tenants, policy)
3. User admin UI
4. Audit export with actor/IP/UA

### WF-10 — Platform operations
1. Flyway migrate-at-start
2. Health / metrics (Micrometer Prometheus)
3. Idempotency cleanup + allocation reconciliation jobs
4. Prod security validator (refuse demo secrets when `%prod`)

---

## 6. Core vs important vs supporting features

### 6.1 Core (must work for any pilot — **built**)

| Core capability | Business risk if broken |
|-----------------|-------------------------|
| Multi-tenant isolation | Data leak across companies |
| Invoice lifecycle + issue | Cannot bill |
| Credit/block enforcement | Uncollectible AR |
| Payments + allocation | Cash not applied |
| Ledger posts on main events | Books untrustworthy |
| Auth + RBAC | Unauthorized financial actions |
| Aging + overdue | Blind collections |
| Audit trail | Compliance failure |

### 6.2 Important differentiators (**built or partial**)

| Differentiator | Status |
|----------------|--------|
| Cheques with true cash application | Done |
| Credit notes that hit AR | Done |
| Customer Email/WhatsApp notifications | Pipeline Done; **real providers Partial** |
| Webhooks for ERP/bank connectors | Done |
| Cheque OCR assist | Partial (client image OCR) |
| Statements + dunning | Foundation Done; PDF/advanced collections pending |

### 6.3 Supporting / platform (**built**)

- FX rate utility
- Tenant registry UI
- Chart-of-accounts seed
- Idempotency, outbox, cleanup jobs
- Docker/prod compose, CI, security scans
- AWS architecture docs (not production-proven here)


---

## 7. Partially built, pending, and improvement opportunities

### 7.1 Partially built (honest residual)

| Item | What works | What’s missing | Priority |
|------|------------|----------------|----------|
| **Email delivery** | Full queue, templates, dispatch, logging sink | Real SMTP/Jakarta Mail or AWS SES client | **P0 for external pilot** |
| **WhatsApp delivery** | Channel model, templates seeded, fail-closed | Meta Cloud API HTTP client + approved templates in Meta | **P0 for WA pilot** |
| **Cheque OCR** | PDF text extract; client Tesseract for images; confidence gate | Server-side image OCR service; higher accuracy ML | P2 |
| **Auth / identity** | Email/password + JWT + refresh + RBAC | Enterprise OIDC/SSO (Keycloak/Cognito/Azure AD) | P1 enterprise |
| **Multi-currency** | Rates + same-currency allocation | Cross-currency settlement + FX gain/loss ledger | P2 |
| **Statements** | JSON/CSV + event | PDF statement + email attachment | P1 collections |
| **Ledger / GL** | AR event journals + seeded COA | Period close, trial balance product, multi-book | P3 product |
| **AWS / cloud** | Terraform modules, CFN, checklists | Live staging env, ALB certs, runbooks exercised | P0 ops for prod |
| **E2E test depth** | Playwright smoke + API smokes | Full critical-path browser suite; multi-tenant IDOR suite | P1 quality |
| **Coverage gate** | JaCoCo configured at 80% | CI currently runs `mvn test` not full `verify` after feature bulk | P1 quality |

### 7.2 Pending features that make the product “top notch”

#### A. Production messaging (highest ROI for “collects, not just records”)

| Feature | Why it matters |
|---------|----------------|
| Real SMTP / SES | Customers actually receive invoices |
| Meta WhatsApp Cloud API | Higher open rates in many markets |
| Provider delivery webhooks (bounce, delivered, read) | Stop spamming bad addresses; compliance |
| Unsubscribe links + quiet hours | Legal/trust for collections outreach |
| PDF invoice attachment | Customer expectation |
| Template preview in UI | Ops confidence before send |
| Channel fallback (WA fail → email) | Reliability |

#### B. Collections excellence

| Feature | Why it matters |
|---------|----------------|
| Promise-to-pay / dispute cases | Structured collections workbench |
| Collector work queues / assignment | Team scale |
| Customer portal (view + pay link) | Self-serve AR |
| Payment links / PSP integration (Stripe etc.) | Faster cash |
| Advanced dunning playbooks | Segmented strategies per customer risk |

#### C. Finance depth

| Feature | Why it matters |
|---------|----------------|
| Cross-currency allocation + FX P&L | Global tenants |
| Period close / posting locks | Controller control |
| Recurring invoices / subscriptions | SaaS AR |
| Revenue recognition hooks (lightweight) | Controllers |
| Bank statement import / reconciliation | Ops efficiency |

#### D. Enterprise platform

| Feature | Why it matters |
|---------|----------------|
| OIDC SSO + SCIM user provisioning | IT security buy-in |
| Fine-grained permissions / custom roles | Large orgs |
| Per-tenant branding | White-label SaaS |
| Multi-region / read replicas | Scale |
| Native image (GraalVM) | Cold-start / cost |
| AP module | Expand TAM |

### 7.3 Features that can be improved (existing surface)

| Area | Improvement | Impact |
|------|-------------|--------|
| Payment list | True cursor pagination, bank-rec filters | Large tenant ops |
| Notifications history | Body preview (secure), resend UX polish | Support cost |
| Dashboard | Cash forecast, collector KPIs, DSO | Management value |
| Invoice UI | Bulk issue, templates, recurring | AR clerk speed |
| Cheque OCR | Operator correction analytics, better payee match | Throughput |
| Webhooks | Admin redrive, signing key rotation UI | Integrator trust |
| Audit | Stream for notification policy/pref changes (QA-016 deferred) | Compliance polish |
| Docs | Working tree currently missing many historical docs (deleted on branch); restore or re-publish from git | Onboarding risk |

> **Note for stakeholders:** Git history still contains full docs (`PROJECT_STATUS`, stories, backlog, AWS, QA). Working tree on this branch shows many `docs/*` deletions — engineering should restore or re-home them so pilots don’t lose institutional knowledge.

---

## 8. Bugs, defects, and known issues

### 8.1 Closed (important historical defects)

| ID | Severity | Summary | Status |
|----|----------|---------|--------|
| DEF-BE-001 | Major | UTF-8 BOM broke Quarkus live reload | **CLOSED** |
| DEF-BE-002 | Major | PaymentResource CDI intermittent failure | **CLOSED** |
| DEF-BE-004 | Major | Application tests failing after ctor expansion | **CLOSED** |
| DEF-FE-001 | Major | Button `size` prop broke TypeScript | **CLOSED** |
| STORY-001…022 correctness holes | P0–P2 | Credit, cheques, reverse, aging, webhooks, etc. | **CLOSED (Done)** |
| QA-NOTIFY-001…015,017–019 | High–Low | Notification security/functional issues | **FIXED** (2026-07-26) |

### 8.2 Open / residual issues

| ID | Severity | Summary | Impact | Suggested action |
|----|----------|---------|--------|------------------|
| DEF-BE-003 | Process | Concurrent multi-agent compile can corrupt `target/classes` | Dev DX only | Process: avoid concurrent `quarkus:dev` mid-compile |
| DEF-BE-006 | Minor | Unrecognized `%dev` datasource username/password key warnings | Log noise | Align config keys |
| DEF-BE-007 | Minor/Ops | Packaged jar cannot switch to H2 via profile alone | Local smoke confusion | Documented — use `quarkus:dev` for H2 |
| DEF-FE-002 | Minor | Some defaults still mention port 8080 | Local setup friction | Standardize 8082 everywhere |
| QA-NOTIFY-016 | Low | Full audit stream for notify policy/pref/manual send | Compliance polish | Sprint backlog |
| SMTP/Meta | **Blocker for external notify** | Providers fail-closed or logging-only | No real customer email/WA | Production Path Sprint 1–2 |
| Schema CHAR vs VARCHAR warnings | Low | Non-fatal DB warnings | Cleanliness | Migration hygiene sprint |
| Coverage gate relaxed in CI | Quality risk | `mvn test` not `verify` after feature bulk | Coverage may drift | Dedicated coverage sprint |
| E2E depth | Quality risk | Smoke only | Regressions in UI flows | Expand Playwright |

### 8.3 Quality gates (latest known)

| Gate | Result (as of last documented runs) |
|------|-------------------------------------|
| Backend unit tests (Surefire aggregate, local) | **647 tests, 0 failures / 0 errors** (this environment) |
| Historical full suite (docs) | ~755–758 tests reported green on 2026-07-24 eng pass |
| Frontend lint / `tsc` | PASS (documented) |
| API smoke scripts | PASS (44/44 on prior eng pass) |
| Notification High QA | Fixed; compile SUCCESS |
| Production ship decision (prior QA) | **Conditional no-ship for full prod** until messaging + ops path complete |

---

## 9. Optimization opportunities

### 9.1 Performance & scale

| Opportunity | Why | Effort |
|-------------|-----|--------|
| Cursor pagination everywhere (invoices/payments/notifications) | Avoid large offset scans | M |
| Read models / materialized aging snapshot | Heavy open-invoice scans as tenants grow | L |
| Partition or archive audit + notification attempts | Table growth | M |
| Connection pool + RLS interceptor load test | Tenant rebind cost under concurrency | M |
| Kafka consumers for inbound bank events | Async scale path already half-built (outbox publish) | L |
| CDN + Next standalone tuning | Console latency | S |
| GraalVM native image (deferred) | Cold start / density | L |

### 9.2 Reliability

| Opportunity | Why |
|-------------|-----|
| Provider idempotency keys (SES/Meta) | At-least-once dispatch already; reduce duplicates |
| Dead-letter dashboards for webhooks + notifications | Ops MTTR |
| Multi-AZ Postgres + PITR backups | Production RPO/RTO |
| Chaos / failure drills on outbox workers | Prove dual-write safety under kill -9 |

### 9.3 Cost / ops

| Opportunity | Why |
|-------------|-----|
| Staging environment from existing Terraform modules | Stop “prod is first cloud env” |
| Structured log sampling + PII policy enforcement in CI | Security + cost |
| Autoscaling ECS/K8s on queue depth (notifications/webhooks) | Match collections spikes |

### 9.4 Product analytics (business)

| Opportunity | Why stakeholders care |
|-------------|----------------------|
| DSO, collection effectiveness, reminder conversion | Prove ROI of notifications |
| OCR correction rate | Justify OCR investment |
| Webhook success rate by tenant | Integration health |

---

## 10. Production readiness scorecard

| Area | Score (1–5) | Comment |
|------|:-----------:|---------|
| Domain correctness (AR core) | **4.5** | Major holes closed in Wave A–B |
| API completeness | **4.5** | Broad REST surface |
| Operator UX | **4.0** | Console covers daily AR; polish remains |
| Security (auth/RBAC/tenant) | **4.0** | Solid phase-1; SSO later |
| Notifications (real delivery) | **2.5** | Pipeline strong; providers stubbed |
| Observability | **3.5** | Metrics/health present; full APM optional |
| Data migrations | **4.5** | Flyway V1–V12 |
| CI/CD | **3.5** | CI green path; coverage verify relaxed |
| Cloud / TLS / secrets | **3.0** | Docs + compose + validator; live cloud unproven |
| Test depth (E2E/load) | **2.5** | Unit strong; load/E2E thin |
| Documentation | **3.0** | Rich in git; working tree gaps |
| **Overall pilot readiness** | **4.0** | Internal pilot GO |
| **Overall external prod readiness** | **2.5–3.0** | Needs Production Path sprints |

### Go / No-Go matrix

| Goal | Verdict |
|------|---------|
| Internal demo / training | **GO** |
| Internal finance pilot (logging email) | **GO** (with known limits) |
| External customer email | **NO-GO** until SES/SMTP real send |
| WhatsApp to real phones | **NO-GO** until Meta client + template approval |
| Multi-tenant SaaS production | **NO-GO** until Production Path + staging burn-in |
| Enterprise RFP (SSO mandatory) | **NO-GO** until OIDC |


---

## 11. Sprint plan — fix, harden, and deliver to production goals

Assumptions: **2-week sprints**, team size per §12, scope focused on AR production (not AP/GL).

### Program overview

| Sprint | Theme | Outcome |
|--------|-------|---------|
| **S0** (1 week) | Stabilize docs & baseline | Docs restored; environments named; metrics dashboard for pilot |
| **S1** | Real email delivery | SES/SMTP live in staging; dual-write proven |
| **S2** | WhatsApp + delivery webhooks | Meta path live; bounce handling MVP |
| **S3** | Collections polish | PDF statement/invoice attach; quiet hours; unsubscribe |
| **S4** | Enterprise security & quality | OIDC option; E2E expansion; coverage gate restored |
| **S5** | Cloud staging burn-in | AWS staging via Terraform; TLS; backups; load smoke |
| **S6** | Controlled production pilot | 1–3 tenants; runbooks; success metrics; go-live checklist |

Total calendar: **~12–14 weeks** to production pilot (aggressive but realistic for current codebase maturity).

---

### Sprint 0 — Baseline & decision pack (1 week)

| Work item | Owner | Done when |
|-----------|-------|-----------|
| Restore/publish authoritative docs from git | Tech lead | `docs/` coherent again |
| Freeze “pilot scope” with PO | PO + TL | Signed scope doc |
| Staging env checklist | DevOps | Names, secrets store, domains |
| Defect scrub (open minors) | Eng | DEF-BE-006/007/FE-002 triaged |

**Exit:** Stakeholders agree Production Path funding.

---

### Sprint 1 — Production email (P0)

| Work item | Type | Est. |
|-----------|------|------|
| Implement SES or SMTP real `EmailSender` | Feature | L |
| Config/secrets, fail-closed validation | Ops | M |
| Staging integration tests + dry-run tenant | QA | M |
| Ops runbook: suppress lists, from-domain, SPF/DKIM | Ops | M |
| Dashboard: send success / fail rates | Feature | S |

**Exit:** Issue invoice in staging → customer inbox receives email.

---

### Sprint 2 — WhatsApp + provider feedback (P0/P1)

| Work item | Type | Est. |
|-----------|------|------|
| Meta Cloud API client (template messages) | Feature | L |
| Seed/map approved template names per tenant | Feature | M |
| Delivery/bounce webhook ingestion MVP | Feature | L |
| Disable auto-sends to hard-bounced destinations | Feature | M |
| Security review of provider callbacks | Security | M |

**Exit:** End-to-end WA send on test numbers; bounce path documented.

---

### Sprint 3 — Collections product polish (P1)

| Work item | Type | Est. |
|-----------|------|------|
| PDF invoice / statement generation | Feature | L |
| Attach PDF on INVOICE_ISSUED email | Feature | M |
| Quiet hours + unsubscribe link | Feature | M |
| Template preview UI | Feature | M |
| Notification policy audit stream (QA-016) | Feature | S |
| Statement send workflow | Feature | M |

**Exit:** Collections can run a “professional” reminder cycle.

---

### Sprint 4 — Security, quality, SSO (P1)

| Work item | Type | Est. |
|-----------|------|------|
| OIDC SSO (Keycloak/Cognito) optional path | Feature | L |
| Expand Playwright critical paths (issue→pay→notify) | QA | M |
| Restore `mvn verify` + coverage remediation | Quality | L |
| Multi-tenant isolation automated tests | Security | M |
| Dependency CVE baseline re-run | Security | S |

**Exit:** CI gate stricter; enterprise login path demoable.

---

### Sprint 5 — Cloud staging & non-functional (P0 ops)

| Work item | Type | Est. |
|-----------|------|------|
| Deploy staging via Terraform modules | DevOps | L |
| TLS certificates, private DB, secrets manager | DevOps | M |
| Backups + restore drill | DevOps | M |
| Load smoke: issue + allocate + dispatch | Perf | M |
| Alerting on worker lag / 5xx / DLQ | Observability | M |
| Penetration test light / checklist | Security | M |

**Exit:** Staging = production-shaped; restore drill signed off.

---

### Sprint 6 — Production pilot (P0 business)

| Work item | Type | Est. |
|-----------|------|------|
| Pilot tenant onboarding (1–3 customers) | PO + CS | M |
| Data migration / seed (if replacing spreadsheet) | Eng | M |
| Training for AR clerks/controllers | PO | S |
| Hypercare + bug bash | All | M |
| Success metrics: DSO proxy, reminder conversion, incidents | PO | S |
| Go / no-go for broader GA | Stakeholders | — |

**Exit criteria for “production pilot success”**

- [ ] 99% of automated invoice emails accepted by provider
- [ ] Zero cross-tenant data incidents
- [ ] Ledger reconciles for pilot period (allocation integrity job clean)
- [ ] P1 defects closed within SLA
- [ ] Controllers sign off on reverse/write-off/credit note flows

---

### Optional parallel tracks (if budget allows)

| Track | Sprints | Notes |
|-------|---------|-------|
| Cross-currency settlement | +2 | After pilot |
| Customer payment portal + PSP | +3–4 | Major differentiator |
| AP module discovery | +1 design | Post AR PMF |
| Advanced collections workbench | +3 | After messaging proven |

---

### Effort rollup (engineering person-sprints, order of magnitude)

| Workstream | Person-sprints |
|------------|---------------:|
| Real email + WhatsApp + bounce | 6–8 |
| Collections polish (PDF, quiet hours, unsub) | 4–5 |
| SSO + security hardening | 3–4 |
| Quality (coverage + E2E) | 3–4 |
| Cloud staging + NFRs | 4–6 |
| Pilot hypercare | 2–3 |
| **Total to production pilot** | **~22–30 person-sprints** |

With a **6-person delivery team**, that is roughly **4–6 calendar sprints** (aligned with the table above), plus Sprint 0.

---

## 12. Team size & role requirements

### 12.1 Recommended team for Production Path (S0–S6)

| Role | Count | Responsibilities |
|------|------:|------------------|
| **Engineering Team Lead / EM** | 1 | Plan, risk, stakeholder sync, architecture calls |
| **Backend engineers (Java/Quarkus)** | 2 | Providers, domain, jobs, ledger, APIs |
| **Frontend engineer (Next.js)** | 1 | Console UX, BFF, notification/collections UI |
| **QA engineer** | 1 | E2E, regression, notification matrix, multi-tenant tests |
| **DevOps / platform** | 1 | AWS, TLS, secrets, CI, observability, backups |
| **Product Owner** (business side) | 1 | Scope, pilot tenants, acceptance, prioritization |
| **Part-time Security review** | 0.25 | Provider webhooks, PII, pen-test checklist |
| **Part-time Designer** (optional) | 0.25 | Collections UX polish |

**Core full-time equivalent:** **~5.5–6.5 FTE** during Production Path.  
**Minimum viable team (slower, ~8–10 sprints):** 1 TL + 1 BE + 1 FE + 0.5 QA + 0.5 DevOps + PO.

### 12.2 Skills required

| Skill | Level |
|-------|-------|
| Java 17, Quarkus, JPA, PostgreSQL | Expert on team |
| Hexagonal / DDD AR domain literacy | At least 1 senior |
| Next.js / React / TypeScript | Solid mid+ |
| Email deliverability (SPF/DKIM/SES) | Available (hire or consult) |
| Meta WhatsApp Business API | Available (or partner) |
| AWS (ECS/ALB/RDS/Secrets) or equivalent | Mid+ DevOps |
| Security (OAuth/OIDC, SSRF, PII) | Review capacity |
| Finance/AR domain (PO or SME) | Mandatory for acceptance |

### 12.3 Team structure suggestion

```
                    Product Owner
                          |
                   Engineering TL
          ________/    |    \________
         /             |             \
   Backend pod    Frontend+BFF    Platform/DevOps
   (2 eng)         (1 eng)         (1 eng)
         \             |             /
          \________  QA (1)  _______/
```

### 12.4 Stakeholder cadence

| Meeting | Cadence | Purpose |
|---------|---------|---------|
| PO ↔ TL backlog refinement | Weekly | Scope control |
| Sprint review + demo | Every 2 weeks | Show working software |
| Risk / production readiness | Bi-weekly | Scorecard §10 |
| Pilot steering (business) | Monthly or at sprint boundaries | Go/No-Go |

---

## 13. Risks & mitigations

| Risk | Likelihood | Impact | Mitigation |
|------|------------|--------|------------|
| Email land in spam / domain not ready | M | High | Early SES setup, warm-up, SPF/DKIM in S1 |
| Meta template approval delays | H | High | Start Business verification in S0; email-first pilot |
| Scope creep (AP/GL/portal) | H | High | PO freeze: Production Path only |
| Coverage drift / regressions | M | Medium | Restore verify gate S4; expand Playwright |
| Cloud first-deploy surprises | M | High | Staging burn-in S5 before pilot tenants |
| Single-person knowledge islands | M | Medium | Pairing; restore docs; runbooks |
| Docs deleted on working branch | H (current) | Medium | Immediate restore from git |
| Underestimating deliverability compliance | M | High | Legal/privacy review for collections messages |

---

## 14. Investment narrative for business stakeholders

### What you’ve already bought (sunk capability)

A **working multi-tenant AR system** with:

- Enforceable billing and cash application
- Cheque and credit-note realism
- Auditability and RBAC
- Collections messaging **architecture** (not just a mock screen)
- Operator console for day-to-day AR
- Path to AWS and hardened prod compose

This is the expensive part of building fintech-adjacent software: **domain correctness**. That investment is largely landed.

### What you still need to buy (production path)

1. **Real customer contact** (email/WhatsApp providers)
2. **Cloud operational maturity** (staging, TLS, backups, alerts)
3. **Enterprise identity** (if selling to IT-gated companies)
4. **Proof** (E2E, load, pilot metrics)

Without (1)–(2), InvoiceGenie remains an excellent **internal AR engine**. With them, it becomes a **collections product** that can defend a production go-live.

### Suggested success metrics for pilot (90 days)

| Metric | Target (illustrative) |
|--------|----------------------|
| % invoices with successful customer notify | ≥ 95% of issued with valid email |
| Time-to-first-reminder automation | 100% policy-driven (no manual chase for in-policy invoices) |
| Allocation integrity job exceptions | 0 material breaks |
| Cross-tenant incidents | 0 |
| P1 production defects open > 5 days | 0 |
| Clerk task time vs spreadsheet baseline | −30% (survey / time study) |

---

## 15. Decision asks for this meeting

Please leave the room with explicit decisions on:

1. **Pilot scope:** Internal only vs named external pilot tenants?
2. **Channels:** Email-only MVP vs Email+WhatsApp in first pilot?
3. **Identity:** Lightweight login sufficient vs OIDC required for pilot?
4. **Cloud target:** AWS (existing IaC) vs other?
5. **Budget / team:** Approve ~6 FTE for 12–14 weeks Production Path?
6. **Out of scope freeze:** Confirm AP, full GL, multi-region deferred?
7. **Success metrics:** Accept §14 metrics or rewrite?


---

## 16. Appendix A — REST surface map (engineering detail)

| Resource | Base path | Highlights |
|----------|-----------|------------|
| Auth | `/api/v1/auth` | login, refresh, logout, me |
| Users | `/api/v1/users` | admin CRUD |
| Tenants | `/api/v1/tenants` | CRUD, activate/suspend |
| Customers | `/api/v1/customers` | CRUD, block, credit-check, ar-summary, statement |
| Invoices | `/api/v1/invoices` | lifecycle, payment shortcut, versions, draft PATCH |
| Payments | `/api/v1/payments` | create/list/get, allocate, reverse, refund, unallocate |
| Cheques | `/api/v1/cheques` | deposit/clear/bounce, bulk, OCR |
| Credit notes | `/api/v1/credit-notes` | apply, available |
| Aging | `/api/v1/aging` | report, buckets, discount |
| Ledger | `/api/v1/ledger` | accounts, balances, validate |
| Exchange rates | `/api/v1/exchange-rates` | CRUD, convert |
| Notifications | `/api/v1/notifications` | history, send, attempts, prefs, policy |
| Webhooks | `/api/v1/webhooks` | subscriptions, deliveries |
| Audit | `/api/v1/audit` | list, entity, CSV export |
| Dunning | `/api/v1/dunning/run` | manual trigger |
| Statements | `/api/v1/customers/{id}/statement` | json/csv |

### Operator console routes

Dashboard · Customers · Invoices · Payments · Cheques · Aging · Credit notes · Ledger · Notifications · Users · Tenants · Webhooks · Audit · Exchange rates · Settings · Login

---

## 17. Appendix B — Story completion snapshot

| Wave | Stories | Status |
|------|---------|--------|
| Wave A correctness + security | STORY-001…004, 007, 008 | **Done** |
| Wave B cash application | STORY-005, 006, 010, 012, 019 | **Done** |
| Wave C productize | STORY-009, 011, 014, 016, 017, 021 | **Done** |
| Wave D collections & GL depth | STORY-013, 015, 018, 020, 022 | **Done** (period close deferred inside 020) |
| QA mini-stories | STORY-QA-001…005 | **Done** |
| Notifications P0 | STORY-NOTIFY-001…012, 021 | **Implemented** (providers logging/fail-closed) |
| Notifications QA High/Med | QA-NOTIFY-001…015,017–019 | **Fixed**; 016 deferred |

---

## 18. Appendix C — Related artifacts

| Artifact | Purpose |
|----------|---------|
| `README.md` | Product + quick start |
| `docs/PROJECT_STATUS.md` (git) | One-page status (2026-07-26) |
| `docs/PRODUCT_OWNER_STORIES.md` (git) | Detailed eng stories |
| `docs/FEATURE_PRIORITY_BACKLOG.md` (git) | P0–P3 backlog |
| `docs/PRODUCTION_READINESS.md` (git) | Machine/prod checklist (partially superseded) |
| `docs/QA_*` (git) | Defects & test report |
| `docs/notifications/*` (git) | Notification product/architecture/QA |
| `docs/aws/*` (git) | Hosting design & IaC |
| `postman/` | API collection |
| `scripts/smoke-ar.mjs`, `test-api.*` | Smoke automation |

---

## 19. Appendix D — Capability maturity (current code reality)

| Capability | Maturity | Top residual gap |
|------------|----------|------------------|
| Customers | **A-** | — |
| Invoices lifecycle | **A-** | Recurring / bulk polish |
| Payments + allocation | **A-** | Cursor pagination |
| Cheques | **A-** | OCR accuracy |
| Cheque OCR | **B-** | Image path client-only |
| Aging + overdue job | **A-** | — |
| Credit notes | **A-** | Broader e2e smoke |
| Ledger (AR subledger) | **B+** | Period close |
| FX rates | **B** | Not in cash application |
| Tenants | **A-** | — |
| Webhooks | **A-** | Redrive UX |
| Audit | **A-** | Notify policy audit stream |
| Idempotency | **A** | — |
| Multi-tenant isolation | **A-** | Automated IDOR suite |
| Outbox/Kafka | **B+** | Consumers deferred |
| AuthN/Z | **B+** | OIDC deferred |
| Collections notifications | **B** | Real providers |
| AP / full GL product | **—** | Deferred modules |

---

## 20. Closing statement (team lead)

InvoiceGenie has crossed the line from “promising AR prototype” to **“credible multi-tenant AR product core.”** The hard domain problems — credit control, cheque cash application, allocation integrity, ledger posts, RBAC, webhooks, and a real notification pipeline — are largely solved in software.

What stands between us and a top-notch **production** product is no longer inventing AR; it is **finishing the last mile**: deliverability, cloud operations, enterprise identity where required, deeper automated proof, and a disciplined pilot with measurable collections outcomes.

With a focused Production Path (approximately **4–6 sprints**, **~6 FTE**), we can move from internal pilot confidence to a production go-live decision backed by evidence — not slides.

---

*Document prepared for the Project Review Sync · InvoiceGenie · 2026-07-27*  
*Next update: after stakeholder decisions on §15 (scope, channels, team, freeze).*

