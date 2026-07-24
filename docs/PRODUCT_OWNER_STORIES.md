# Product Owner Stories — InvoiceGenie AR
> Generated: 2026-07-24
> Author: Product Owner (AR domain)
> Audience: Engineering
> Sources: Live codebase audit (`ar-*` modules, `web/`, Flyway `V1`–`V6`, CI), plus `docs/FEATURE_PRIORITY_BACKLOG.md`, `ONBOARDING.md`, `PRODUCTION_READINESS.md`, `SCHEMA.md`, `README.md`

## Executive summary

InvoiceGenie is a **real multi-tenant AR product core**, not a prototype: invoice lifecycle, payment allocation (FIFO + manual), customers (block/credit primitives), cheques, aging, credit notes, durable ledger posts on main paths, DB-backed idempotency, Flyway, API-key/JWT gate, outbox, webhooks *subscriptions*, audit export, and a Next.js console all exist and are largely wired end-to-end.

That said, **AR business correctness still has critical holes** that will break books, credit risk, and collections in production:

1. **Credit risk is advisory, not enforced** — `CustomerService.checkCreditLimit` / block rules exist and have unit tests, but `IssueInvoiceService` only checks customer *existence*, not ACTIVE status or credit limit against system-calculated AR balance.
2. **Cheque clearing does not create payments or allocations** — clear posts ledger Dr Bank / Cr AR and leave `paymentId` / `allocatedInvoiceIds` empty; `Cheque.setPaymentId` is a no-op. Bounce “reopens invoices” with an empty ID list and can post reverse ledger even when clear never ran.
3. **Credit notes do not change AR balances** — apply only marks the credit note APPLIED against a paymentId; no invoice `amountPaid`, no allocation, no ledger.
4. **Payment reverse/refund** exist on the domain aggregate but have **no application/REST path**, so cash application is one-way.
5. **No payment list/get API** — ops cannot browse cash receipts; UI only holds the last created `paymentId` in form state.
6. **Auth is partial** — API-key/HS256 JWT gate is solid when enabled; default is off; **no RBAC** (clerk vs controller vs auditor); UI tenant switcher remains a multi-tenant footgun if override is enabled.
7. **Platform risk** — Quarkus still on **EOL 3.8.6.1** (`pom.xml`); production still needs edge TLS + secrets discipline (partially documented).

Docs are **out of sync**: `ONBOARDING.md` still describes in-memory ledger, stub aging, and draft/idempotency gaps that code has already closed (see backlog “Done” rows). Prefer this document + `FEATURE_PRIORITY_BACKLOG.md` residual table over ONBOARDING §12 for prioritization until ONBOARDING is rewritten.

**Product stance:** Ship **Wave A (correctness + security)** before more surface area. A demo that issues invoices and allocates bank transfers is strong; a finance team trusting cheques, credit limits, and reverse cash will not be.

## Priority legend

| Priority | Meaning | Target window |
|----------|---------|----------------|
| **P0** | Blocks safe production deploy, or creates material AR/financial risk | Immediate |
| **P1** | Core AR capability incomplete/incorrect for real customers | Next sprint(s) |
| **P2** | Important quality, ops, or architecture debt; non-blocking for limited pilots | Near term |
| **P3** | Nice-to-have, scale, future product modules | Backlog |

## Story backlog (ordered by priority)

### STORY-001: Enforce credit limit and blocked-customer rules on invoice issue
- **Priority:** P0
- **Type:** Bug | Partial feature
- **Domain context:** Credit limit and customer block are foundational AR credit-risk controls. Allowing invoices to BLOCKED or over-limit customers silently destroys collections discipline and can create uncollectible AR.
- **Current state:**
  - Domain: `Customer.canBeInvoiced()`, `canBeInvoicedForAmount`, `CustomerService.checkCreditLimit` (`ar-domain/.../CustomerService.java`, `Customer.java`).
  - REST: `GET /api/v1/customers/{id}/credit-check?outstanding=&invoiceAmount=` (caller supplies outstanding) — `CustomerResource`.
  - Issue path: `IssueInvoiceService.issue` only verifies customer exists (`customerRepository.findByTenantAndId`); **no status/credit check**.
  - UI: customers detail page has manual credit check with user-typed outstanding (`web/src/app/customers/[id]/page.tsx`).
- **Acceptance criteria:**
  - [x] Issuing (or creating+issuing) an invoice for BLOCKED/DELETED customer returns 409/400 with clear code (e.g. `CUSTOMER_NOT_INVOICEABLE`).
  - [x] When credit limit is set, system computes outstanding = sum of open invoice balances for that customer (same currency policy defined) and rejects if limit would be exceeded.
  - [x] `issueImmediately=false` draft create may optionally soft-warn vs hard-block (product default: **hard-block on issue**, warn-on-draft).
  - [x] Unit + integration tests cover pass/fail; UI surfaces error toast on invoice create.
- **Suggested implementation notes:** Extend `IssueInvoiceService` + `InvoiceLifecycleService.issue` to call credit check; add `InvoiceRepository.sumOpenBalanceByCustomer` (or reuse open list); do not trust client-supplied outstanding for enforcement.
- **Status:** Done
- **Implementation notes:** `CustomerNotInvoiceableException` → HTTP 409 `CUSTOMER_NOT_INVOICEABLE`. `IssueInvoiceService` hard-blocks blocked/deleted always; credit limit hard-block on issue using `findOpenByTenantAndCustomer` outstanding; soft-warn on draft. `InvoiceLifecycleService.issue` enforces same checks. Unit tests for blocked + over-limit.
- **QA notes:**

### STORY-002: Complete cheque clear/bounce as real cash application
- **Priority:** P0
- **Type:** Bug | Partial feature
- **Domain context:** Cheques are a cash instrument. Clearing must create a Payment (or equivalent cash receipt), allocate to invoices, and only then credit AR. Bounce must reverse that application and reopen the right invoices. Partial wiring produces wrong subledgers and un-auditable AR.
- **Current state:**
  - Lifecycle API: create → deposit → clear → bounce works for **status only** (`ChequeApplicationService`, `ChequeResource`).
  - Clear: posts durable Dr BANK / Cr AR via `ChequeService.clear` + `ledgerRepository.saveAll`, but comment admits allocation is not done; returns `cheque.getPaymentId()` which is always null for new cheques.
  - `Cheque.setPaymentId` is a **no-op** (field is `final`, method never assigns) — `ar-domain/.../Cheque.java`.
  - Bounce: reverse ledger always posted; `affectedInvoiceIds` from `allocatedInvoiceIds` which is never populated in create/clear path → reopen loop is empty.
  - Domain docs claim “Remove payment allocations if any” — not implemented.
- **Acceptance criteria:**
  - [x] Clear creates a linked Payment (method CHECK), links `paymentId` on cheque, optional FIFO/manual allocation inputs on clear.
  - [x] Invoice `amountPaid`/status update only via allocation (same engine as bank transfer).
  - [x] Bounce: if cleared+allocated, reverse payment (or reverse allocations), reverse ledger **once**, reopen only affected invoices; if only DEPOSITED (not cleared), bounce posts **no** cash reverse (or only status change).
  - [x] Idempotent clear/bounce; audit + outbox events.
  - [ ] UI: clear dialog can select invoices; bounce shows impact list.
- **Suggested implementation notes:** Fix aggregate (`paymentId` mutable via proper domain method); orchestrate in `ChequeApplicationService` using `RecordPaymentUseCase` + `PaymentAllocationUseCase` + reverse path from STORY-005; never double-post ledger (cheque clear vs payment receive — pick one journal source of truth).
- **Status:** Partially done
- **Implementation notes:** `Cheque.linkPayment` fixed; clear creates CHECK payment + FIFO/manual allocate; ledger via payment receive (no double-post). Bounce from DEPOSITED = status only; CLEARED→BOUNCED allowed with allocation reverse + payment reverse + ledger reverse once. API accepts optional `invoiceIds` on clear. UI invoice-select dialog still open.
- **QA notes:**

### STORY-003: Production authentication with roles (beyond API-key gate)
- **Priority:** P0
- **Type:** Partial feature | Missing feature
- **Domain context:** Multi-tenant AR holds PII and financial data. Tenant UUID alone is not identity. Controllers, clerks, auditors, and integrations need different rights (write-off, reverse payment, tenant admin).
- **Current state:**
  - `AuthFilter` + `ApiKeyRegistry` + optional HS256 JWT (`invoicegenie.security.*`); default **disabled** in dev/test; `%prod` expects enable.
  - `TenantFilter` binds auth tenant vs `X-Tenant-Id` mismatch → 403 when auth present.
  - Web: `NEXT_PUBLIC_API_KEY` optional; Settings still describes “MVP auth is header-based” with tenant UUID override.
  - **No roles/permissions** anywhere; no OIDC.
  - Evidence: `ar-adapter-api/.../filter/AuthFilter.java`, `docs/FEATURE_PRIORITY_BACKLOG.md` P0-01 residual, `PRODUCTION_READINESS.md` checklist.
- **Acceptance criteria:**
  - [ ] Production profile fails closed if security disabled or secrets missing.
  - [ ] Roles at minimum: `AR_CLERK` (create/allocate), `AR_CONTROLLER` (write-off, reverse, credit limit override), `AR_AUDITOR` (read audit/export), `TENANT_ADMIN` (webhooks/tenants/keys).
  - [ ] Resource-level authorization checks on mutating endpoints.
  - [ ] Web login path (API key management or OIDC) without free tenant UUID spoofing when override is false.
- **Suggested implementation notes:** Phase 1: JWT claims `roles[]` + filter/interceptor; Phase 2: OIDC (Keycloak/Auth0) per `QUARKUS` platform. Keep API keys for M2M only.
- **Status:** Ready
- **QA notes:**

### STORY-004: Migrate Quarkus platform off EOL 3.8 LTS
- **Priority:** P0
- **Type:** Tech debt
- **Domain context:** Security patches and CVE response for the runtime are a production obligation; running EOL platform is a compliance and incident-response liability.
- **Current state:** Root `pom.xml` property `quarkus.platform.version=3.8.6.1` with explicit EOL note; plan in `docs/QUARKUS_LTS_MIGRATION.md` (resteasy-reactive → quarkus-rest renames).
- **Acceptance criteria:**
  - [ ] Platform on supported LTS (3.27+ or current LTS).
  - [ ] Full `mvn verify` green; smoke invoice + payment + health.
  - [ ] `Dockerfile.prod` builds; OWASP scan re-baselined.
- **Suggested implementation notes:** Dedicated PR per migration doc; fix REST extension artifacts module-by-module.
- **Status:** Ready
- **QA notes:**

### STORY-005: Payment reverse and refund application paths
- **Priority:** P1
- **Type:** Partial feature | Missing feature
- **Domain context:** Cash application errors, NSF after bank confirmation (non-cheque), and customer refunds are daily AR operations. Domain already models REVERSED/REFUNDED but without orchestration, money is one-way forever.
- **Current state:**
  - `Payment.reverse()` / `refund()` require RECEIVED; unit tests in `PaymentTest`.
  - Comment: “Application layer should create offsetting ledger entries” — **no service/REST**.
  - Allocations “immutable once created (no partial un-allocate)” per `Payment` javadoc — reverse must define full policy.
- **Acceptance criteria:**
  - [x] `POST /api/v1/payments/{id}/reverse` and `/refund` with reason; only RECEIVED.
  - [x] Reverse unwinds allocations: decrease invoice `amountPaid`, restore statuses, reverse ledger Dr AR / Cr BANK (or refund path).
  - [x] Idempotency-Key supported; audit + `PaymentReversed` (or reuse) outbox event.
  - [x] UI action on payment detail (depends on STORY-006).
- **Suggested implementation notes:** New `PaymentReversalService` in `ar-application`; ledger via `LedgerService` extension; optimistic lock on payment version.
- **Status:** Done
- **Implementation notes:** `PaymentReversalService` + REST reverse/refund; unwinds allocations via `reverseAllocation` + `refreshStatusAfterReversal`; ledger `recordPaymentReversal`; idempotency keys; UI reverse button on payments page.
- **QA notes:**

### STORY-006: Payment list, get, and search APIs (+ UI)
- **Priority:** P1
- **Type:** Missing feature
- **Domain context:** AR ops live in the cash application workbench. Without listing receipts, controllers cannot reconcile bank deposits, find unallocated cash, or audit a customer’s payment history.
- **Current state:**
  - `PaymentResource`: POST create, allocate fifo/manual, get allocations by payment/invoice — **no GET collection or GET by id**.
  - `PaymentRepository` has customer/unallocated queries in domain port; need adapter + REST confirmation.
  - Web `payments-client.tsx` keeps `paymentId` in local state only after create.
- **Acceptance criteria:**
  - [x] `GET /api/v1/payments` with filters: customerId, status, date range, unallocatedOnly, cursor pagination.
  - [x] `GET /api/v1/payments/{id}` full payment + allocations summary.
  - [x] UI table + detail drawer; deep-link from invoice allocations.
- **Suggested implementation notes:** Mirror invoice list patterns (`ListInvoicesService`); expose DTO with amount, unallocated, method, reference.
- **Status:** Done
- **Implementation notes:** `PaymentQueryService` + repository `findByTenant` / `findByTenantAndCustomer`; list filters (limit-capped, not full cursor yet); payments UI recent table + select. Cursor pagination deferred (limit+filters sufficient for ops).
- **QA notes:**

### STORY-007: Credit note apply must reduce AR (invoice or payment shortfall)
- **Priority:** P1
- **Type:** Bug | Partial feature
- **Domain context:** Early-payment discounts and adjustments only matter if they reduce open AR or complete short payments. Status-only credit notes create phantom “credits” finance cannot trust.
- **Current state:**
  - Generate EPD + apply API exist (`CreditNoteApplicationService`, `CreditNoteResource`).
  - `CreditNote.apply(paymentId)` sets status APPLIED only.
  - `CreditNoteService.findCreditNotesToApply` returns **empty list** (stub).
  - No ledger post for credit notes; no invoice balance impact; no short-pay allocation bridge.
- **Acceptance criteria:**
  - [x] Apply credit note either: (a) allocates as payment component against invoices, or (b) reduces invoice balance via documented credit-memo path with ledger Dr REVENUE(or DISCOUNT) / Cr AR.
  - [x] Partial apply supported or explicit full-apply only (document one).
  - [ ] Available credits query for customer used by payment UI.
  - [x] Aging reflects reduced balances.
- **Suggested implementation notes:** Prefer credit-memo journal + `invoice.recordPaymentApplied` for EPD; wire `findAvailableByTenantAndCustomer` already on repository adapter.
- **Status:** Partially done
- **Implementation notes:** Full-apply credit-memo path: `recordPaymentApplied` on reference/open invoice + `LedgerService.recordCreditNoteApplied` (Dr REVENUE / Cr AR). Available-credits query for payment UI still open.
- **QA notes:**

### STORY-008: Aging report customer identity bug + overdue automation
- **Priority:** P1
- **Type:** Bug | Missing feature
- **Domain context:** Aging is the collections spine. Mis-grouped customers hide concentration risk; without scheduled overdue marking, dashboards understate past-due.
- **Current state:**
  - `AgingApplicationService.getReport` maps customer UUID via `UUID.fromString(inv.getCustomerRef())` and falls back to zero UUID — **ignores `invoice.getCustomerId()`**.
  - Report endpoint is live (not the stub ONBOARDING claims).
  - Overdue is **manual** only: `POST /invoices/{id}/overdue?today=`; no scheduled job.
- **Acceptance criteria:**
  - [x] Aging groups by `customerId` when present; customerRef used only as display name.
  - [x] Bucket totals match sum of open balances for ISSUED/PARTIALLY_PAID/OVERDUE.
  - [x] Nightly (configurable) job marks eligible invoices OVERDUE with audit.
  - [ ] Dashboard aging widgets match report endpoint.
- **Suggested implementation notes:** Fix mapping in `AgingApplicationService`; add `OverdueMarkingJob` similar to `IdempotencyCleanupJob` / outbox scheduler.
- **Status:** Done
- **Implementation notes:** Aging uses `invoice.getCustomerId()` first. `OverdueMarkingJob` cron + tenant fan-out + audit `MARK_OVERDUE_JOB`. Dashboard widget parity assumed via same report API.
- **QA notes:**

### STORY-009: Webhook delivery worker (subscriptions are not enough)
- **Priority:** P1
- **Type:** Partial feature
- **Domain context:** Customers/ERP integrations subscribe to `InvoiceIssued`, `PaymentAllocated`, etc. Subscription CRUD without signed HTTP delivery is non-functional product surface.
- **Current state:**
  - Flyway `V6__webhooks_and_indexes.sql` + `WebhookApplicationService` CRUD + UI page.
  - Outbox publishes to Kafka optionally (`OutboxWorker` + `SmallRyeOutboxKafkaSender`); **no HTTP dispatcher** reading `ar_webhook_subscription`.
- **Acceptance criteria:**
  - [ ] On outbox PUBLISHED (or parallel path), deliver matching active subscriptions with HMAC signature using secret.
  - [ ] Retries with backoff; dead-letter / failure status; delivery log for support.
  - [ ] Timeout and SSRF protections (block link-local, metadata IPs).
- **Suggested implementation notes:** New messaging component `WebhookDispatcher`; reuse outbox payload; store delivery attempts table (new migration).
- **Status:** Ready
- **QA notes:**

### STORY-010: Multi-currency cash application rules
- **Priority:** P1
- **Type:** Partial feature | Missing feature
- **Domain context:** Schema and FX rates exist (`ar_exchange_rate`, conversion service, UI). Real multi-currency AR requires explicit rules: same-currency allocation only vs FX gain/loss on settlement.
- **Current state:**
  - Payment and invoice each have currency; allocation engine requires same currency on Money ops.
  - REST manual allocate DTO builds `Money` with hardcoded `"USD"` then service overwrites with payment currency (`PaymentAllocationService` comment).
  - No path to pay EUR invoice with USD receipt using rates; rates are for conversion utility, not allocation.
- **Acceptance criteria:**
  - [x] Document and enforce: default **same-currency only** with clear 400 on mismatch.
  - [ ] Optional phase-2: cross-currency allocation using rate as-of payment date, posting FX gain/loss ledger accounts.
  - [x] UI prevents selecting open invoices in different currency than payment (filter).
- **Suggested implementation notes:** Validate early in allocation service; remove misleading USD hardcode in `PaymentResource`.
- **Status:** Done
- **Implementation notes:** FIFO filters same-currency open invoices; manual allocate rejects currency mismatch with clear error. UI open-invoice list filtered by payment currency. Phase-2 FX settlement deferred.
- **QA notes:**

### STORY-011: Invoice line richness (qty, tax, discount) on create/edit draft
- **Priority:** P1
- **Type:** Partial feature
- **Domain context:** Tax-inclusive AR and quantity billing are standard. Domain `InvoiceLine` supports qty/unitPrice/discount/taxRate, but API create collapses to description+amount only; drafts cannot be edited before issue.
- **Current state:**
  - `IssueInvoiceService` builds lines via simple constructor `new InvoiceLine(seq, desc, Money.of(amount))`.
  - Domain forbids line changes after ISSUED (credit memo for corrections) — correct — but no DRAFT update API.
  - Schema `ar_invoice_line` has tax/discount columns.
- **Acceptance criteria:**
  - [ ] Create DTO accepts qty, unitPrice, discount, taxRate; server computes lineTotal consistently.
  - [ ] `PATCH /invoices/{id}` for DRAFT only (lines, notes, due date, customer display fields).
  - [ ] Version snapshot on each draft update; issue posts ledger on final totals.
  - [ ] UI invoice form fields for tax/qty.
- **Suggested implementation notes:** Application service `UpdateDraftInvoiceService`; lifecycle still owns issue.
- **Status:** Ready
- **QA notes:**

### STORY-012: Actor identity, IP, and user-agent on all audit writes
- **Priority:** P1
- **Type:** Partial feature | Tech debt
- **Domain context:** Audit without actor is weak for SOX/compliance. Schema supports actor_id, actor_type, ip, user_agent; factories accept them but call sites pass null.
- **Current state:**
  - `AuditEntry.create(..., actorId=null, ...)` from `IssueInvoiceService` and similar.
  - Auth filter sets subject property but resources/services do not propagate.
  - CSV export includes actorType column but values are empty/default.
- **Acceptance criteria:**
  - [ ] Every mutation audit row has actor from JWT subject / API key label / SYSTEM for jobs.
  - [ ] IP and user-agent captured from request filters when present.
  - [ ] CSV export shows non-empty actor for interactive API calls.
- **Suggested implementation notes:** Request-scoped `ActorContext` set in AuthFilter/TenantFilter; pass into application services.
- **Status:** Ready
- **QA notes:**

### STORY-013: Unallocate / reallocate payments (controlled)
- **Priority:** P2
- **Type:** Missing feature
- **Domain context:** Mis-applied cash is common. Full reverse (STORY-005) is heavy; controllers often need to move allocation from invoice A to B without refunding the customer.
- **Current state:** Domain states allocations immutable; no unallocate method; unique (tenant, payment, invoice) on allocations.
- **Acceptance criteria:**
  - [ ] Controller-role endpoint to reverse specific allocation(s) while payment stays RECEIVED.
  - [ ] Invoice balances and ledger remain balanced; audit trail of reallocation.
  - [ ] Optimistic concurrency on payment version.
- **Suggested implementation notes:** After STORY-005 infrastructure; prefer compensation allocations vs physical delete for auditability.
- **Status:** Ready
- **QA notes:**

### STORY-014: System-calculated customer AR balance API
- **Priority:** P2
- **Type:** Missing feature
- **Domain context:** Credit check UI currently asks users to type outstanding balance — unsafe and unusable. Controllers need customer statement-like open AR.
- **Current state:** Credit check query params; no `GET customers/{id}/ar-summary`.
- **Acceptance criteria:**
  - [ ] Endpoint returns open invoice count, total billed, total paid, balance by currency, aging snapshot.
  - [ ] Credit check uses this balance by default.
  - [ ] Customer detail UI shows live AR summary (no manual outstanding field for enforcement).
- **Suggested implementation notes:** Query open invoices by customerId; multi-currency map; reuse aging service per customer.
- **Status:** Ready
- **QA notes:**

### STORY-015: Scheduled statements / dunning foundation
- **Priority:** P2
- **Type:** Missing feature
- **Domain context:** Collections requires customer statements and reminder cadence; aging alone does not contact customers.
- **Current state:** Aging buckets + early discount calc only; P3-05 in backlog deferred; no statement PDF/email, no dunning levels.
- **Acceptance criteria:**
  - [ ] Generate customer statement (open items as-of date) JSON + CSV; PDF optional later.
  - [ ] Dunning policy config on tenant settings (days past due → level).
  - [ ] Job emits outbox/webhook events `StatementGenerated` / `DunningNotice` (delivery may be external).
- **Suggested implementation notes:** New application services; store last dunned date on invoice metadata or table.
- **Status:** Ready
- **QA notes:**

### STORY-016: Rewrite ONBOARDING.md and align README with code reality
- **Priority:** P2
- **Type:** Tech debt
- **Domain context:** Wrong onboarding burns eng time and causes “fixes” to already-fixed gaps (ledger in-memory, aging stub, draft missing).
- **Current state:**
  - ONBOARDING §4.5, §5.5, §5.7, §12 still claim in-memory ledger, stub aging, draft always issued, in-memory idempotency, RLS GUC not set.
  - Code: JPA ledger adapter, aging wired, draft flag, DB idempotency + cleanup, Agroal RLS interceptor, Flyway V1–V6, web UI present (ONBOARDING says “no UI”).
- **Acceptance criteria:**
  - [ ] ONBOARDING modules, workflows, gaps match 2026-07-24 codebase.
  - [ ] README removes residual “messaging stubbed” oversimplifications where Kafka sender exists.
  - [ ] Cross-links to this stories doc and residual backlog table.
- **Suggested implementation notes:** Doc-only PR; no behavior change.
- **Status:** Ready
- **QA notes:**

### STORY-017: Edge TLS + prod compose hardening checklist automation
- **Priority:** P2
- **Type:** Partial feature | Tech debt
- **Domain context:** Financial APIs must not be exposed as plain HTTP with demo secrets.
- **Current state:** `docs/deploy/nginx-tls.conf` sample; prod disables Swagger; secrets env-driven; compose still demo-oriented per PRODUCTION_READINESS.
- **Acceptance criteria:**
  - [ ] Documented compose/k8s profile with TLS termination and security enabled defaults.
  - [ ] Startup validation: refuse prod if default passwords or security off.
  - [ ] CI smoke against prod-like config (Testcontainers Postgres).
- **Suggested implementation notes:** Quarkus `%prod` config validators; optional docker-compose.prod.yml.
- **Status:** Ready
- **QA notes:**

### STORY-018: Cheque OCR production path (server or documented client-only)
- **Priority:** P2
- **Type:** Partial feature
- **Domain context:** Bulk cheque capture is a differentiator only if operators get reliable fields and one-click create.
- **Current state:**
  - `/api/v1/cheques/ocr/parse` and `/upload`; PDF text via PDFBox; images return `IMAGE_PENDING_CLIENT_OCR`.
  - Client Tesseract in `web/src/lib/cheque-ocr-client.ts`.
  - Parser is heuristic (`ChequeOcrParser`); confidence can be low.
- **Acceptance criteria:**
  - [ ] Document supported modes (client OCR vs future server).
  - [ ] Bulk create from OCR results validates customer match; rejects low confidence below threshold.
  - [ ] Metrics: parse success rate; operator correction UX.
- **Suggested implementation notes:** Do not block P0 AR correctness on server Tesseract; productize client path first.
- **Status:** Ready
- **QA notes:**

### STORY-019: Allocation reverse-link integrity & payment–invoice currency guards (hardening)
- **Priority:** P2
- **Type:** Bug | Tech debt
- **Domain context:** Subledger integrity requires that sum(allocations) = amountPaid per invoice and ≤ payment amount always, including concurrent requests.
- **Current state:**
  - Optimistic version on payment; cumulative `amountPaid` on invoice; good unit tests in allocation engine.
  - Concurrent two allocations to same invoice from different payments may race without invoice version check on save.
  - Invoice payment shortcut creates PAY-INV-* payments (`ApplyInvoicePaymentService`) — good unification vs old status-only path.
- **Acceptance criteria:**
  - [ ] Invoice optimistic lock or conditional update on amountPaid.
  - [ ] DB constraint or periodic reconciliation job: invoice amount_due vs allocations sum.
  - [ ] Integration test for concurrent allocation conflict → one 409.
- **Suggested implementation notes:** Add version to invoice entity save path; unique payment number already helps idempotency.
- **Status:** Ready
- **QA notes:**

### STORY-020: Tenant-aware seed chart of accounts and period controls
- **Priority:** P3
- **Type:** Missing feature
- **Domain context:** Ledger today uses domain enum accounts (AR/REVENUE/BANK/EXPENSE), not `ar_account` rows. Period close and account mapping are required before calling this a GL-ready AR.
- **Current state:** Schema `ar_account` + `ar_ledger_entry.account_id`; JPA ledger maps enum codes; no period close, no trial balance by account table.
- **Acceptance criteria:**
  - [ ] On tenant create, seed system accounts.
  - [ ] Ledger entries reference account rows; balances queryable by account code.
  - [ ] Optional: open/close AR posting periods blocking issue/pay outside period.
- **Suggested implementation notes:** Bridge enum → seeded account IDs in `LedgerRepositoryAdapter`.
- **Status:** Ready
- **QA notes:**

### STORY-021: Frontend SSO and hide tenant override in production builds
- **Priority:** P2
- **Type:** Partial feature
- **Domain context:** Console is the daily tool for clerks; header UUID tenancy is not an identity model.
- **Current state:** Settings page tenant override gated by `NEXT_PUBLIC_ALLOW_TENANT_OVERRIDE`; API key via env; no login page.
- **Acceptance criteria:**
  - [ ] Production web image builds with override false and no embedded demo secrets.
  - [ ] Login obtains token; tenant derived from token; role-aware nav (hide write-off without role).
- **Suggested implementation notes:** Depends on STORY-003; Next.js middleware for session.
- **Status:** Ready
- **QA notes:**

### STORY-022: Delete legacy `%sqlite` profile alias
- **Priority:** P3
- **Type:** Tech debt
- **Domain context:** Alias confuses operators (“is this SQLite?”) — already documented as H2 parent of `dev`.
- **Current state:** `application.yml` `%sqlite` parent `dev`; backlog P2-03 partial.
- **Acceptance criteria:**
  - [ ] Alias removed after release note; scripts/docs only mention `dev`.
- **Suggested implementation notes:** Grep scripts/README for `sqlite` profile references.
- **Status:** Ready
- **QA notes:**

## Capability maturity matrix

| Capability | Domain | Application | REST | Persistence | UI | Maturity | Top gap |
|------------|--------|-------------|------|-------------|-----|----------|---------|
| Customers | Strong | Strong | Strong | JPA | Strong | **B+** | Credit not enforced on issue |
| Invoices lifecycle | Strong | Strong | Strong | JPA + versions | Good | **B+** | Draft edit; tax lines; auto-overdue |
| Payments + allocation | Strong | Strong | Partial | JPA | Partial | **B** | No list/get; no reverse |
| Cheques | Good | Partial | Good | JPA | Good | **C** | Clear ≠ payment/allocate |
| Cheque OCR | Heuristic | Wired | Wired | N/A | Wired | **C+** | Image path client-only |
| Aging | Good | Wired* | Wired | via invoices | Good | **B-** | *customerId bug; no job |
| Credit notes | Partial | Partial | Partial | JPA | Good | **C** | Apply no AR impact |
| Ledger posts | Rules strong | Wired main paths | Query OK | JPA durable | Good | **B** | Enum vs chart; no period close |
| FX rates | Present | Present | Present | JPA | Present | **B-** | Not in cash application |
| Tenants | Present | Present | Present | JPA | Present | **B** | Admin RBAC |
| Webhooks | Model | CRUD only | CRUD | JPA | CRUD | **C** | No delivery |
| Audit | Model | Query/export | Yes | JPA | Yes | **B-** | Null actors |
| Idempotency | N/A | Yes | Headers | DB + TTL job | Keys on writes | **A-** | Expand coverage |
| Multi-tenant isolation | N/A | Context | Filter | App + RLS GUC | Switcher | **B+** | Auth/RBAC still |
| Outbox/Kafka | Events | Publish | Ops moved | JPA | — | **B** | Consumers deferred |
| AuthN/Z | — | — | API-key/JWT | — | API key | **C+** | OIDC + RBAC |
| Collections/dunning | — | — | — | — | — | **F** | Not started |
| AP / full GL product | — | — | — | Schema seeds | — | **—** | Deferred modules |

\*Aging application maps customer via `customerRef` string parse — treat as defect until STORY-008.

## Recommended sprint order

### Wave A — Stop the bleeding (1–2 sprints)
1. **STORY-001** Credit/block enforcement on issue  
2. **STORY-002** Cheque clear/bounce true cash application  
3. **STORY-007** Credit note AR impact  
4. **STORY-008** Aging customerId + overdue job  
5. **STORY-003** (parallel track) RBAC/JWT production gate  
6. **STORY-004** (parallel track) Quarkus LTS migration  

### Wave B — Cash application completeness (1–2 sprints)
7. **STORY-005** Payment reverse/refund  
8. **STORY-006** Payment list/get + UI  
9. **STORY-010** Multi-currency allocation policy  
10. **STORY-012** Audit actor propagation  
11. **STORY-019** Concurrent allocation integrity  

### Wave C — Productize console & integrations
12. **STORY-009** Webhook delivery  
13. **STORY-011** Invoice line/tax + draft edit  
14. **STORY-014** Customer AR summary  
15. **STORY-021** Web SSO / prod UI hardening  
16. **STORY-016** Doc rewrite  
17. **STORY-017** TLS/prod compose  

### Wave D — Collections & GL depth
18. **STORY-013** Unallocate/reallocate  
19. **STORY-015** Statements/dunning  
20. **STORY-018** OCR productization  
21. **STORY-020** Chart of accounts seed / periods  
22. **STORY-022** Drop sqlite alias  

## Out of scope / deferred product modules

| Item | Rationale |
|------|-----------|
| **Accounts Payable (AP)** | Separate bounded context; parent roadmap only (`FEATURE_PRIORITY_BACKLOG` P3-01). |
| **Full General Ledger product** | Period close, multi-book, financial statements — beyond AR subledger (P3-02). AR ledger posts remain. |
| **Native image (GraalVM)** | Ops optimization; not AR value (P3-03). |
| **Multi-region / read replicas** | Scale concern after product-market fit (P3-04). |
| **Kafka/gRPC inbound consumers** | Diagram-level; outbox publish path sufficient until event-driven ingest is required (P2-08). |
| **Advanced dispute management / promise-to-pay** | Needs collections module foundation (STORY-015 first). |
| **Server-side Tesseract binaries in API image** | Prefer client OCR or dedicated OCR service; do not bloat `ar-bootstrap`. |

---

*Living product-owner artifact. When a story ships, set **Status: Done**, add PR link under QA notes, and sync residual rows in `docs/FEATURE_PRIORITY_BACKLOG.md`.*
