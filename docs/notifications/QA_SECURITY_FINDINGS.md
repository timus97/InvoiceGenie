# QA & Security Findings — Customer Notifications MVP

**Product:** InvoiceGenie multi-tenant AR  
**Scope:** Email + WhatsApp notification P0 MVP  
**Reviewer role:** Senior QA Engineer / Security Tester  
**Date:** 2026-07-25  
**Sources reviewed:** product/architecture/implementation docs, V11 migration, domain/application/messaging/API/web, unit tests, `application.yml`

---

## Executive summary

| Severity | Count |
|----------|------:|
| **Critical** | **0** |
| **High** | **4** |
| **Medium** | **10** |
| **Low** | **5** |
| **Total developer stories** | **19** |

**Go / No-Go for pilot:** **CONDITIONAL GO** (internal pilot only; logging provider only)

| Audience | Recommendation |
|----------|----------------|
| Internal demo / pilot (`email.provider=logging`, WhatsApp off) | **GO** after fixing or explicitly accepting High items #001–#002 (policy force + SKIPPED lock) |
| External customer email (real SMTP/SES) | **NO-GO** until High PII logging, force semantics, SKIPPED re-queue, and dual-write/retry design are addressed |
| WhatsApp to real phones (Meta) | **NO-GO** (no real client, no WA templates seeded, always-logging resolver) |

---

## 1. Test plan summary (what was verified / how)

Review method: **static code review + design cross-check against P0 stories** (no live SMTP/Meta traffic; unit tests inspected, not re-executed in this session).

| Area | Paths / artifacts | Method | Result |
|------|-------------------|--------|--------|
| Product / AC | `docs/notifications/PRODUCT_OWNER_NOTIFICATIONS.md` | Map STORY-NOTIFY-001…012,021 to code | Mostly implemented; gaps below |
| Architecture | `docs/notifications/ARCHITECTURE_NOTIFICATIONS.md` | Pattern vs webhook/outbox/dunning | Matches hexagonal layout |
| Implementation notes | `docs/notifications/IMPLEMENTATION_NOTES.md` | Claim vs code (force, RLS, seeds) | **force claim mismatches code** |
| Schema / RLS | `ar-bootstrap/.../V11__notifications.sql` | RLS policies, uniqueness, seeds, FKs | RLS present; weak FKs on customer/invoice; owner bypass documented |
| Domain | `ar-domain/.../model/notification/*` | Status machine, idempotency keys, renderer | Sound; SKIPPED + unique key interaction is risky |
| Enqueue | `NotificationEnqueueService` | Policy, prefs, templates, force, opt-out | Opt-out OK; force too broad; SKIPPED persists forever |
| Jobs / bridge | `NotificationOutboxBridge`, `PaymentReminderJob`, `NotificationDispatchWorker` | Idempotency, scheduling, retries | Day-miss reminders; dual-write risk; no SKIP LOCKED |
| Providers | `LoggingEmailSender`, `SmtpEmailSender`, `LoggingWhatsAppSender` | Side effects, secrets | Logging PII; SMTP is success stub |
| REST + RBAC | `NotificationResource` + `@RequireRoles` | Authz matrix, tenant context | Solid RBAC annotations; tenant filter in repos |
| Config / secrets | `application.yml` `invoicegenie.notifications.*` | Env placeholders | Tokens via env (good); defaults enable notifications |
| Frontend | `/notifications`, invoice send, customer prefs, settings policy | force flag, role gates | Admin policy UI OK; send always `force: true` |
| Unit tests | `Notification*Test`, `NotificationEnqueueServiceTest` | Coverage vs risk | Good core enqueue cases; **no** worker/API/RBAC/dispatch tests |

**Not executed here:** end-to-end issue→outbox→enqueue→dispatch against running Quarkus; multi-tenant IDOR probes with two JWTs; Meta/SMTP integration.

---

## 2. Functional quality issues

| ID | Sev | Summary |
|----|-----|---------|
| QA-NOTIFY-001 | High | SKIPPED rows permanently block later real sends (same idempotency key) |
| QA-NOTIFY-002 | High | `force=true` bypasses more than auto flags; UI always sends force |
| QA-NOTIFY-005 | Medium | Unknown invoice → `202` + empty list (not 404) |
| QA-NOTIFY-006 | Medium | Manual/auto enqueue does not require ISSUED/open invoice status |
| QA-NOTIFY-007 | Medium | Manual send drops PAYMENT_REMINDER / DUNNING qualifiers |
| QA-NOTIFY-008 | Medium | `preDueDays = 0` silently falls back to config default (3) |
| QA-NOTIFY-009 | Medium | Pre-due job exact-date match only → missed day never retried |
| QA-NOTIFY-013 | Medium | SMTP “provider” returns success without sending; WA never uses Meta |
| QA-NOTIFY-017 | Low | History UI lacks filters, attempts drill-down, body/error detail |
| QA-NOTIFY-018 | Low | WhatsApp templates not seeded → always `NO_TEMPLATE` if channel enabled |

## 3. Security issues

| ID | Sev | Category | Summary |
|----|-----|----------|---------|
| QA-NOTIFY-002 | High | Authz / policy | Clerks can bypass tenant “notifications disabled” / channel off via force |
| QA-NOTIFY-003 | High | PII | Destination + message body logged at INFO on send path |
| QA-NOTIFY-004 | High | Reliability / abuse | Provider success + DB commit not dual-write safe → duplicate customer messages under retry |
| QA-NOTIFY-010 | Medium | Injection / SSRF-prep | `destinationOverride` not validated (email/E.164/CRLF) |
| QA-NOTIFY-011 | Medium | Integrity | No FK for preference/notification → customer/invoice; orphan prefs OK |
| QA-NOTIFY-012 | Medium | Multi-instance | `findDue` without row lock → double dispatch if scaled out |
| QA-NOTIFY-014 | Medium | Abuse | No rate limit on `POST /notifications/send` |
| QA-NOTIFY-015 | Low | Hardening | Invalid UUID path params can 500 |
| QA-NOTIFY-016 | Low | Audit | No actor audit for policy/pref/manual send |
| QA-NOTIFY-019 | Low | Integrity | Pref upsert does not verify customer exists in tenant |

**Explicitly checked and OK (no finding):**

- Tenant isolation on read/write paths uses `TenantContext` + `tenant_id` predicates (`NotificationRepositoryAdapter.findById`, list, attempts).
- RLS enabled on all five notification tables in V11 (owner bypass for workers is documented).
- Opt-out is honored even when `force=true` (consent path stronger than product wording “automated only”).
- Template renderer is substitution-only (`{{var}}`), not expression evaluation — low SSTI risk.
- Message **body** not exposed on REST DTO (destination/subject are).
- Secrets (`SMTP_PASSWORD`, WhatsApp token) are env-driven, not hard-coded.
- Policy PUT restricted to `TENANT_ADMIN`; send excludes `AR_AUDITOR`.

---

## 4. Severity ratings used

| Level | Meaning |
|-------|---------|
| **Critical** | Tenant isolation break, auth bypass, or mass customer contact / secret leak in default pilot path |
| **High** | Wrong policy/consent outcomes, permanent send suppression, PII leakage in logs, or production duplicate-send design flaw |
| **Medium** | Functional AC miss, abuse surface, integrity gap, or pilot footgun when misconfigured |
| **Low** | UX, hardening, coverage, or P1 polish |

---

## 5. Developer stories (one per issue)

### STORY-QA-NOTIFY-001

| Field | Value |
|-------|--------|
| **Title** | Do not permanently lock out sends after SKIPPED idempotency rows |
| **Severity** | High |
| **Owner area** | backend (application + domain) |
| **Files** | `ar-application/.../NotificationEnqueueService.java`, `ar-domain/.../Notification.java`, possibly repository |

**Description**  
Any first attempt that creates a `SKIPPED` row (e.g. `NO_DESTINATION`, `OPTED_OUT`, `NO_TEMPLATE`, `POLICY_DISABLED`) stores the same unique `(tenant_id, idempotency_key)` as a future real send. Later fixes (add email, re-enable channel, re-consent) return the existing SKIPPED row and never enqueue PENDING.

**Steps to reproduce**

1. Customer with **no email**; issue invoice → outbox enqueues `INVOICE_ISSUED` → row `SKIPPED` / `NO_DESTINATION`.
2. Add customer email.
3. Manual send or re-issue path with same key → API returns same SKIPPED id; dispatch never runs.

**Acceptance criteria**

- [ ] SKIPPED for recoverable reasons does **not** block a later successful enqueue (delete/supersede key, or key includes reason generation, or allow re-enqueue when status=SKIPPED and reason ∈ recoverable set).
- [ ] Final terminal outcomes that must stay unique (`SENT` per dunning level/channel) still de-dupe.
- [ ] Unit tests: skip NO_DESTINATION → add email → second enqueue creates PENDING.
- [ ] Opt-out then re-enable can send again for events that have not been SENT (product decision documented).

---

### STORY-QA-NOTIFY-002

| Field | Value |
|-------|--------|
| **Title** | Align `force` with “override auto flags only”; stop UI always forcing |
| **Severity** | High |
| **Owner area** | backend + frontend |
| **Files** | `NotificationEnqueueService.java`, `NotificationApplicationService.java`, `web/src/app/invoices/[id]/page.tsx`, docs |

**Description**  
`NotificationUseCase` and `IMPLEMENTATION_NOTES.md` claim force only skips auto-policy flags. Code also bypasses `globalEnabled`, `policy.enabled`, and `channel` global enables when `force=true`. Frontend invoice button hard-codes `force: true`, so any `AR_CLERK` can send while tenant admin disabled notifications/email.

**Steps to reproduce**

1. TENANT_ADMIN: Settings → disable notifications (or email channel) → save.
2. As AR_CLERK: open invoice → **Send notification (email)**.
3. Observe PENDING/SENT (logging) instead of SKIPPED / POLICY_DISABLED or CHANNEL_DISABLED.

**Acceptance criteria**

- [ ] `force` only bypasses event auto flags (`autoSendOnIssue` / pre-due / dunning event toggles), **not** master/channel/global disable (unless a separate elevated flag + TENANT_ADMIN-only).
- [ ] Invoice UI default `force: false` (or omit); optional admin “override policy” control if product wants it.
- [ ] Unit + API tests for force matrix.
- [ ] Docs updated to match.

---

### STORY-QA-NOTIFY-003

| Field | Value |
|-------|--------|
| **Title** | Redact PII from notification dispatch logs |
| **Severity** | High |
| **Owner area** | backend (messaging) + config |
| **Files** | `LoggingEmailSender.java`, `LoggingWhatsAppSender.java`, `SmtpEmailSender.java`, `NotificationDispatchWorker.java` |

**Description**  
Senders log full destination and truncated body at INFO. Dispatch worker logs destination on SENT. In shared log pipelines this is customer PII (email/phone + invoice amounts in template body).

**Acceptance criteria**

- [ ] Production profile: no full destination/body at INFO; use hashed/masked destination + notification id.
- [ ] Demo logging sink may remain verbose only when explicitly enabled (e.g. `invoicegenie.notifications.log-payloads=true`).
- [ ] Document retention implications for ops.

---

### STORY-QA-NOTIFY-004

| Field | Value |
|-------|--------|
| **Title** | Make dispatch dual-write safe before real providers |
| **Severity** | High (prod); Medium impact while provider=logging |
| **Owner area** | backend (messaging) |
| **Files** | `NotificationDispatchWorker.java`, `NotificationRepositoryAdapter.findDue` |

**Description**  
`processDue` is one `@Transactional` that marks SENDING, calls provider, marks SENT. Provider side effects are not transactional with DB. Retry after partial failure can re-deliver. Multi-instance lacks `FOR UPDATE SKIP LOCKED`.

**Acceptance criteria**

- [ ] Per-notification short transactions (or outbox-style claim) with optimistic/pessimistic claim of due rows.
- [ ] Document at-least-once vs at-most-once; prefer provider idempotency keys where available.
- [ ] Claim query uses row locking or lease so two workers cannot dispatch the same row.
- [ ] Integration test for concurrent claim (or documented single-instance constraint for pilot).

---

### STORY-QA-NOTIFY-005

| Field | Value |
|-------|--------|
| **Title** | Return 404 when manual send targets unknown invoice |
| **Severity** | Medium |
| **Owner area** | backend |
| **Files** | `NotificationEnqueueService.java`, `NotificationResource.java`, `NotificationApplicationService.java` |

**Description**  
`enqueueForInvoice` returns empty list when invoice missing; REST always responds `202` with `[]`, masking client bugs and cross-checks.

**Acceptance criteria**

- [ ] Missing invoice → 404 `NOT_FOUND`.
- [ ] Empty channel resolution vs missing invoice distinguishable.
- [ ] API test.

---

### STORY-QA-NOTIFY-006

| Field | Value |
|-------|--------|
| **Title** | Guard notification enqueue by invoice lifecycle |
| **Severity** | Medium |
| **Owner area** | backend |
| **Files** | `NotificationEnqueueService.java` |

**Description**  
Enqueue only checks invoice existence. DRAFT (or cancelled/written-off) can receive `INVOICE_ISSUED` manual notifications with rendered totals.

**Acceptance criteria**

- [ ] `INVOICE_ISSUED` / reminders / dunning only for allowed statuses (e.g. ISSUED, PARTIALLY_PAID, OVERDUE as product defines).
- [ ] Unit tests for DRAFT rejection.

---

### STORY-QA-NOTIFY-007

| Field | Value |
|-------|--------|
| **Title** | Manual send must compute correct idempotency qualifiers |
| **Severity** | Medium |
| **Owner area** | backend |
| **Files** | `NotificationApplicationService.sendForInvoice` |

**Description**  
Manual path calls `qualifierFor(type, null, null)`, so PAYMENT_REMINDER loses `due:yyyy-MM-dd` and DUNNING loses `L{level}` — parallel keys vs automated jobs, breaking “one send per dunning level” semantics.

**Acceptance criteria**

- [ ] Manual PAYMENT_REMINDER uses invoice due date qualifier.
- [ ] Manual DUNNING requires level (request field or invoice state) and uses `L{n}`.
- [ ] Tests for key parity with auto path.

---

### STORY-QA-NOTIFY-008

| Field | Value |
|-------|--------|
| **Title** | Honor `preDueDays = 0` in PaymentReminderJob |
| **Severity** | Medium |
| **Owner area** | backend |
| **Files** | `PaymentReminderJob.java` (`policy.getPreDueDays() > 0 ? … : default`) |

**Description**  
DB allows `pre_due_days` 0–90. Job treats `0` as “use default 3”, so same-day reminders never run as configured.

**Acceptance criteria**

- [ ] Use `>= 0` from policy when row exists; config default only when policy missing.
- [ ] Unit/job test with preDueDays=0 → targetDue = today.

---

### STORY-QA-NOTIFY-009

| Field | Value |
|-------|--------|
| **Title** | Pre-due reminders must not silently miss a day |
| **Severity** | Medium |
| **Owner area** | backend |
| **Files** | `PaymentReminderJob.java` |

**Description**  
Only invoices with `dueDate == today + preDueDays` are selected. If the hourly job fails or instance is down that calendar day, reminder never fires (idempotency key never created).

**Acceptance criteria**

- [ ] Select open invoices with due date in a window (e.g. due ∈ [today+days, today+days] with catch-up for due ≤ target and not yet reminded), **or** document ops SLA + alerting as accepted MVP risk.
- [ ] If catch-up implemented: still one send per due-date qualifier.

---

### STORY-QA-NOTIFY-010

| Field | Value |
|-------|--------|
| **Title** | Validate destination overrides (format + CRLF) |
| **Severity** | Medium |
| **Owner area** | backend (+ light frontend validation) |
| **Files** | `NotificationPreferenceApplicationService.java`, `NotificationEnqueueService.resolveDestination` |

**Description**  
`destinationOverride` accepts any string ≤320 chars. Used as email/phone. Enables misdelivery and future SMTP header injection (`\r\n`) when real transport lands.

**Acceptance criteria**

- [ ] EMAIL: basic RFC-ish validation; reject CR/LF/control chars.
- [ ] WHATSAPP: E.164 (or documented format); reject CR/LF.
- [ ] API 400 on invalid override.

---

### STORY-QA-NOTIFY-011

| Field | Value |
|-------|--------|
| **Title** | Add referential integrity for notification customer/invoice FKs |
| **Severity** | Medium |
| **Owner area** | backend (migration + preference service) |
| **Files** | `V11__notifications.sql` (follow-up migration), preference upsert |

**Description**  
Unlike core AR tables, `ar_notification_preference.customer_id` and notification `customer_id`/`invoice_id` lack composite FKs to tenant-scoped parents. Prefs can be written for random UUIDs.

**Acceptance criteria**

- [ ] Prefer composite FKs aligned with schema style **or** application-level existence checks before upsert/enqueue.
- [ ] Pref upsert returns 404 if customer missing in tenant.

---

### STORY-QA-NOTIFY-012

| Field | Value |
|-------|--------|
| **Title** | Claim due notifications safely under concurrency |
| **Severity** | Medium |
| **Owner area** | backend (persistence + worker) |
| **Files** | `NotificationRepositoryAdapter.findDue`, `NotificationDispatchWorker` |

**Description**  
Due poll is unordered claim without locking. Fine for single pilot instance; unsafe for multi-replica.

**Acceptance criteria**

- [ ] Document “single dispatch instance” for pilot **or** implement SKIP LOCKED / lease.
- [ ] If multi-instance allowed, integration evidence of no double SENT for one id.

---

### STORY-QA-NOTIFY-013

| Field | Value |
|-------|--------|
| **Title** | Fail closed when SMTP/Meta not actually implemented |
| **Severity** | Medium |
| **Owner area** | backend / config |
| **Files** | `SmtpEmailSender.java`, `NotificationDispatchWorker.resolveWhatsAppSender` |

**Description**  
`SmtpEmailSender` logs and returns **success**. WhatsApp resolver always prefers `LoggingWhatsAppSender`, ignoring `whatsapp.provider=meta`. Misconfiguration can report SENT to operators while customers receive nothing (or only logs).

**Acceptance criteria**

- [ ] `provider=smtp` without real transport → FAILED/clear error, not SENT.
- [ ] `provider=meta` without client → fail closed; do not silently log-success.
- [ ] Config validation at startup in prod profile (warn/fail).

---

### STORY-QA-NOTIFY-014

| Field | Value |
|-------|--------|
| **Title** | Rate-limit manual notification send |
| **Severity** | Medium |
| **Owner area** | backend |
| **Files** | `NotificationResource.send` |

**Description**  
Authenticated clerks can hammer `POST /api/v1/notifications/send`. Idempotency limits duplicates per key but not fan-out across invoices/channels or force spam of different events.

**Acceptance criteria**

- [ ] Per-tenant and/or per-actor rate limit (or reuse existing API throttle if added).
- [ ] Document limits for pilot.

---

### STORY-QA-NOTIFY-015

| Field | Value |
|-------|--------|
| **Title** | Validate UUID path/query params on notification routes |
| **Severity** | Low |
| **Owner area** | backend |
| **Files** | `NotificationResource.java` |

**Description**  
`get` / `attempts` / `byInvoice` call `UUID.fromString` outside try/catch → possible 500 vs 400.

**Acceptance criteria**

- [ ] Invalid UUID → 400 `VALIDATION_ERROR`.
- [ ] Consistent with other resources.

---

### STORY-QA-NOTIFY-016

| Field | Value |
|-------|--------|
| **Title** | Audit trail for policy, preferences, and manual send |
| **Severity** | Low |
| **Owner area** | backend |
| **Files** | preference/policy/application services, audit integration |

**Description**  
Delivery attempts are stored, but who changed consent/policy or triggered manual send is not written to the existing audit stream (STORY-012 style).

**Acceptance criteria**

- [ ] Audit events for policy update, preference upsert, manual send (actor, tenant, target ids).
- [ ] Visible in audit API/UI or documented export path.

---

### STORY-QA-NOTIFY-017

| Field | Value |
|-------|--------|
| **Title** | Notification history UX: filters + attempt detail |
| **Severity** | Low |
| **Owner area** | frontend |
| **Files** | `web/src/app/notifications/page.tsx`, API client |

**Description**  
List shows destination/subject/status but no filter by status/event, no attempts drawer (API exists: `listNotificationAttempts`).

**Acceptance criteria**

- [ ] Filter by status and event type (client or server).
- [ ] Expand row or detail route showing attempts from `GET .../attempts`.

---

### STORY-QA-NOTIFY-018

| Field | Value |
|-------|--------|
| **Title** | Seed or document WhatsApp template gap |
| **Severity** | Low |
| **Owner area** | config / backend |
| **Files** | `V11__notifications.sql`, product docs |

**Description**  
Only EMAIL system templates seeded. Enabling WHATSAPP channel yields `NO_TEMPLATE` and SKIPPED (and then STORY-QA-NOTIFY-001 lock).

**Acceptance criteria**

- [ ] Either seed WA template metadata for MVP logging path **or** product clearly “email-only MVP” and UI disables WA until templates exist.
- [ ] Policy UI warning when WHATSAPP selected without templates.

---

### STORY-QA-NOTIFY-019

| Field | Value |
|-------|--------|
| **Title** | Reject preference upsert for unknown customers |
| **Severity** | Low |
| **Owner area** | backend |
| **Files** | `NotificationPreferenceApplicationService.java` |

**Description**  
Upsert does not load customer; combined with missing FK (011), orphan preference rows are possible via API.

**Acceptance criteria**

- [ ] 404 if customer not in tenant.
- [ ] Test.

---

## 6. Positive findings (what works well)

1. **Hexagonal fit** — Domain ports, application enqueue, persistence adapters, messaging workers, REST, and web mirror webhook/outbox patterns without a new deployable.
2. **Schema + RLS** — V11 creates templates, preferences, policy, queue, attempts with tenant policies and useful indexes (`uq_notif_idempotency`, due partial index). Worker RLS bypass is **documented** (not silent).
3. **Idempotency design (intent)** — Key shape `notify:{event}:{invoiceId}:{channel}[:qualifier]` matches architecture for issue / due-date / dunning level.
4. **Opt-out honored on all enqueue paths** — Including `force` (stronger consent than product “automated only”); covered by `NotificationEnqueueServiceTest.skipOptOut`.
5. **Safe template rendering** — Regex substitution only; `Matcher.quoteReplacement` used; unknown vars → empty (unit tested).
6. **RBAC annotations** — List/get for clerks/controllers/auditors/admins; send for operational roles; policy write TENANT_ADMIN-only.
7. **App-level tenant checks** on find-by-id (empty if wrong tenant) reduce IDOR risk even if RLS GUC mis-set for owner connections.
8. **Body not on list DTO** — Reduces casual PII over REST vs full message dump (destination still exposed for ops — acceptable for AR staff).
9. **Config flags** — Global/email/WhatsApp enables, provider switch, max attempts, dispatch interval, SMTP/Meta env placeholders.
10. **Frontend completeness for MVP** — Sidebar history, invoice send, customer prefs card, admin policy section behind `isTenantAdmin`.
11. **Race on unique key** — Enqueue catches save races and reloads by idempotency key.
12. **Outbox bridge isolation** — Notification failures are caught in `OutboxWorker` so customer notify errors do not poison event publish.

---

## 7. Recommended regression tests to add

| Priority | Test | Layer |
|----------|------|--------|
| P0 | SKIPPED NO_DESTINATION then add email → re-enqueue PENDING | unit (`NotificationEnqueueService`) |
| P0 | force=true does **not** bypass policy.enabled / emailEnabled | unit |
| P0 | force=true still SKIPPED on OPTED_OUT | unit (exists) — keep |
| P0 | Opt-out then re-enable behavior (after 001 fix) | unit |
| P0 | Manual send missing invoice → 404 | API / resource test |
| P1 | DRAFT invoice manual send rejected | unit |
| P1 | Manual DUNNING/REMINDER qualifier matches auto keys | unit |
| P1 | preDueDays=0 selects due=today | unit (`PaymentReminderJob` extractable logic) |
| P1 | Destination override rejects CRLF / bad email | unit |
| P1 | RBAC: AR_AUDITOR cannot POST send / PUT prefs; cannot PUT policy | `RoleAuthorizationFilter` style API test |
| P1 | Tenant A cannot read notification id of tenant B | persistence/API |
| P2 | Dispatch claims row once under concurrency | integration |
| P2 | Outbox `InvoiceIssued` → enqueue PENDING (with logging provider) | integration |
| P2 | FE: policy disabled → send UX messaging | e2e optional |
| P2 | Template renderer with `$` and `\` in values (quoteReplacement) | unit (extend) |

**Existing tests (keep green):**

- `NotificationTemplateRendererTest`
- `NotificationIdempotencyKeysTest`
- `NotificationEnqueueServiceTest` (opt-out, idempotency happy path, enqueue success)

**Gaps today:** no tests for `NotificationDispatchWorker`, `NotificationOutboxBridge`, `PaymentReminderJob`, `NotificationResource`, preference/policy services, or frontend notification flows.

---

## 8. Go / No-Go recommendation

### Pilot (internal, logging email only)

**CONDITIONAL GO**

**Must for a clean pilot:**

1. Accept or fix **STORY-QA-NOTIFY-002** (force/UI) — otherwise policy toggles are theater.
2. Accept or fix **STORY-QA-NOTIFY-001** (SKIPPED lock) — otherwise first missing email permanently bricks issue-notify for that invoice.
3. Keep **`INVOICEGENIE_NOTIFICATIONS_EMAIL_PROVIDER=logging`** and **WhatsApp disabled**.
4. Do **not** set `email.provider=smtp` expecting real delivery (**STORY-QA-NOTIFY-013**).
5. Single app instance for dispatch (or accept double-send risk — **012**).

**Should before external pilot customers:**

- PII log redaction (**003**)
- Dual-write / claim design (**004**)
- Manual send 404 + status guards (**005**, **006**)
- Destination validation (**010**)

### Production customer messaging

**NO-GO** until High stories 001–004 and 013 are closed, real provider implemented and fail-closed, and regression suite above at P0 is automated.

---

## 9. Traceability (story → primary evidence)

| Story | Primary evidence |
|-------|------------------|
| 001 | `NotificationEnqueueService` lines ~106–109 + `saveSkipped` + `uq_notif_idempotency` |
| 002 | `enqueueOne` force checks; `web/.../invoices/[id]/page.tsx` `force: true`; notes claim “auto flags only” |
| 003 | `LoggingEmailSender` / `LoggingWhatsAppSender` / dispatch SENT log |
| 004 | `NotificationDispatchWorker.processDue` `@Transactional` + external `send` |
| 005 | `enqueueForInvoice` empty on missing invoice; `NotificationResource.send` 202 |
| 006 | No status check in enqueue |
| 007 | `NotificationApplicationService` `qualifierFor(type, null, null)` |
| 008 | `PaymentReminderJob` `preDueDays > 0 ? …` |
| 009 | Exact `dueDate.equals(targetDue)` loop |
| 010 | `setDestinationOverride` unvalidated |
| 011 | V11 preference/notification DDL vs `ar_customer` FKs elsewhere |
| 012 | `findDue` JPQL without lock |
| 013 | `SmtpEmailSender` `SendResult.ok`; WA resolve always logging |
| 014 | No throttle on send endpoint |
| 015 | Uncaught `UUID.fromString` on GET paths |
| 016 | No audit calls in notification app services |
| 017 | `notifications/page.tsx` list-only |
| 018 | V11 EMAIL-only template seeds |
| 019 | Preference upsert without customer load |

---

## 10. Counts + decision (quick reference)

| Severity | Stories | Count |
|----------|---------|------:|
| Critical | — | **0** |
| High | 001, 002, 003, 004 | **4** |
| Medium | 005, 006, 007, 008, 009, 010, 011, 012, 013, 014 | **10** |
| Low | 015, 016, 017, 018, 019 | **5** |
| **Total** | | **19** |

**Go/No-Go: CONDITIONAL GO (internal, logging-only pilot) / NO-GO (real customer Email/WhatsApp delivery).**
