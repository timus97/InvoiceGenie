# QA Test Report — InvoiceGenie AR

> **Date:** 2026-07-24  
> **Author:** Senior QA (AR)  
> **Scope:** Backend unit/integration tests, frontend static checks, API smoke (H2 `dev` profile on port **8082**), code review vs `docs/PRODUCT_OWNER_STORIES.md`  
> **Branch/workspace:** local `Development` with uncommitted Senior Developer WIP  
> **Servers:** Quarkus `quarkus:dev` on `http://0.0.0.0:8082` (stopped after run). Port 8080 occupied by unrelated Apache — not used.

---

## Environment

| Item | Value |
|------|--------|
| OS | Windows (PowerShell) |
| Backend | Quarkus **3.8.6.1** (`ar-bootstrap`, profile `dev`, H2 mem, Kafka Dev Services off) |
| Frontend | Next.js 15.5.20 / React 19 / TypeScript 5 (`web/`) |
| Test tenant | `00000000-0000-0000-0000-000000000001` |
| Security | `invoicegenie.security.enabled=false` (dev default) |
| Smoke script | `scripts/test-api.ps1 -BaseUrl http://localhost:8082` |
| Artifacts | `docs/qa-api-smoke-final.log`, `docs/qa-mvn-test*.log` (if present) |

---

## What was tested

### Backend automated
1. **Baseline (pre/mid-WIP):** full `mvn test` → **755 tests, 0 failures, 0 errors** (245 suites).
2. **During Senior Developer WIP:** intermittent failures and compile breaks:
   - UTF-8 **BOM** (`\ufeff`) on multiple new/edited `.java` files broke Quarkus live-reload Eclipse compiler.
   - Incomplete class output (`TenantStatus.class` missing) prevented `quarkus:dev` restart mid-flight.
   - `ar-application` suite reported **2 errors** (`NoClassDefFoundError` on test class load for `ChequeApplicationServiceTest` / `IssueInvoiceServiceTest`) — constructor / classpath churn under parallel edits.
3. **`mvn compile`** after clean rebuild: **PASS**.
4. Packaged `quarkus-run.jar` **cannot** switch to H2 at runtime (build-time Postgres driver binding) — smoke correctly uses `quarkus:dev`.

### Frontend automated
| Check | Result |
|-------|--------|
| `npm run lint` | **PASS** |
| `npx tsc --noEmit` | **FAIL** (see DEF-FE-001) |
| `npm run build` (earlier baseline) | **PASS** (before FE regression) |

### API smoke (final clean run)
`scripts/test-api.ps1` against `http://localhost:8082`:

| Metric | Result |
|--------|--------|
| **PASS** | **27** |
| **FAIL** | **0** |
| Exit code | **0** |

Covered: health, customers CRUD-ish, invoice create/get/list, payment create/allocate/get allocations, aging, cheques create/list/deposit/clear, credit notes create/list, outbox stats, ledger validate, DRAFT invoice + issue, payment idempotency replay.

### Extended manual API probes (story-focused)
| Probe | Result | Story |
|-------|--------|-------|
| Issue invoice for **BLOCKED** customer (with `dueDate`) | **409** `CUSTOMER_NOT_INVOICEABLE` | STORY-001 |
| Missing `dueDate` on invoice create | **400** `dueDate is required` (before credit check) | STORY-001 / UX |
| Cheque clear links `paymentId` + `PaymentRecorded` outbox | **PASS** | STORY-002 |
| `GET/POST /api/v1/payments` list/get/create | **PASS** (after CDI stable) | STORY-006 / 005 |
| `POST .../payments/{id}/reverse` | **200** status `REVERSED` | STORY-005 |
| OCR `POST /api/v1/cheques/ocr/parse` | **200** fields extracted | STORY-018 |
| Aging report with open invoice | **200** buckets / grandTotal | STORY-008 |
| Tenants list / ledger accounts | **200** | — |
| Intermittent `PaymentResource` CDI create failure | **400** empty-ish body: *Unable to create class ... make sure this class is a CDI bean* | DEF-BE-002 |

### Frontend manual code review
| Area | Notes |
|------|-------|
| Invoices line description | Multi-line `Textarea` + amount; **no** qty/tax/discount (STORY-011 open) |
| Cheques OCR bulk | Client Tesseract + server parse/upload; review grid + bulk create |
| Payments | Create + allocate; WIP list UI uses `size="sm"` on `Button` → **TS error** |
| Aging | Report + buckets + discount calculator wired |
| Settings | Tenant UUID override; health check proxies via Next rewrite to **8080** by default (`BACKEND_URL`) |

---

## Pass/Fail matrix (summary)

| Area | Status | Detail |
|------|--------|--------|
| Backend unit tests (baseline) | **PASS** | 755/755 |
| Backend tests under concurrent WIP | **FLAKY / FAIL** | BOM, missing classes, test classloader errors |
| Frontend lint | **PASS** | |
| Frontend `tsc --noEmit` | **FAIL** | DEF-FE-001 |
| Frontend production build | **PASS** (baseline); **at risk** after FE regression | Re-run after fixing size prop |
| API smoke script | **PASS** | 27/27 on clean `quarkus:dev` :8082 |
| STORY-001 block enforce | **PASS** (smoke) | 409 + code; credit-limit not fully smoke-tested |
| STORY-002 cheque clear → payment | **PARTIAL PASS** | paymentId set; full bounce/unallocate policy not fully exercised |
| STORY-005 reverse path | **PARTIAL PASS** | reverse works; refund / allocated unwind lightly exercised |
| STORY-006 payment list/get | **PARTIAL PASS** | API works when CDI ok; UI incomplete / TS break |
| STORY-003 / 004 auth & Quarkus LTS | **FAIL vs AC** | Still open by design (not Done) |
| STORY-007 credit note AR impact | **NOT FULLY VERIFIED** | Create works; apply→balance not fully asserted in smoke |
| STORY-008 aging customerId | **LIKELY FIXED in WIP** | Code uses `customerId` first; overdue job present in tree |
| STORY-009 webhook delivery | **OPEN** | Subscriptions only |
| Live-reload stability during multi-agent edit | **FAIL** | DEF-BE-001, DEF-BE-003 |

---

## Defects

### DEF-BE-001 — UTF-8 BOM on Java sources breaks Quarkus live reload  
- **Severity:** **Major** (Blocker for local hot-reload / concurrent agent work)  
- **Story:** STORY-QA-001 (process); impacts delivery of STORY-001/002/005/006  
- **Steps:** Developer agent writes/edits `.java` with UTF-8 BOM → `quarkus:dev` RuntimeUpdates → `illegal character: '\ufeff'` → endpoints return **500**  
- **Expected:** Sources are UTF-8 without BOM; live reload succeeds  
- **Actual:** Multiple files under `ar-application`, `ar-adapter-api`, etc. contained EF BB BF  
- **Mitigation used by QA:** Stripped BOMs to continue testing (do **not** reintroduce)

### DEF-BE-002 — `PaymentResource` intermittent CDI instantiation failure  
- **Severity:** **Major** (Blocker when it hits production-like traffic during WIP)  
- **Story:** STORY-005, STORY-006  
- **Steps:** `GET/POST /api/v1/payments` during unstable CDI / dual constructors without `@Inject`  
- **Expected:** Resource is always a CDI bean; list/create return 2xx  
- **Actual:** `400 VALIDATION_ERROR` message: *Unable to create class '…PaymentResource'. To fix the problem, make sure this class is a CDI bean.*  
- **Note:** After clean restart, list/create/get/reverse **passed**. Fix: single `@Inject` constructor (4-arg), remove 2-arg overload or mark properly.

### DEF-BE-003 — Concurrent WIP leaves domain classes incomplete → `quarkus:dev` fails to start  
- **Severity:** **Major**  
- **Story:** STORY-QA-001  
- **Evidence:** `PathTreeClassPathElement … expected to provide …/TenantStatus.class but failed`  
- **Expected:** Atomic module compile before live reload  
- **Actual:** Partial `target/classes` mid-edit

### DEF-BE-004 — Application module tests fail to load after constructor expansion  
- **Severity:** **Major** (CI gate risk)  
- **Story:** STORY-001, STORY-002  
- **Evidence:** `ChequeApplicationServiceTest` / `IssueInvoiceServiceTest` → `NoClassDefFoundError: InvoiceLifecycleUseCase` / `IdGenerator` (test class metadata)  
- **Expected:** Nested Mockito tests pass with updated constructors  
- **Actual:** 2 suite-level **errors** during WIP `mvn test` on `ar-application`

### DEF-BE-005 — Invoice create without `dueDate` fails validation before credit/block semantics  
- **Severity:** **Minor** (product/docs; can mask STORY-001 in tests)  
- **Story:** STORY-001  
- **Steps:** POST invoice without `dueDate`  
- **Expected:** Documented requirement **or** default due date from payment terms  
- **Actual:** `400 dueDate is required` (blocked customer only returns 409 when dueDate present)

### DEF-BE-006 — Unrecognized Quarkus config keys under `%dev`  
- **Severity:** **Minor**  
- **Story:** STORY-022 / config hygiene  
- **Evidence:** WARN `quarkus.datasource.jdbc.username` / `password` ignored (should be `quarkus.datasource.username` / `password`)

### DEF-BE-007 — Packaged jar does not run with H2 via runtime profile alone  
- **Severity:** **Minor** (docs/ops)  
- **Story:** STORY-017  
- **Evidence:** `Driver does not support the provided URL: jdbc:h2:…` on `quarkus-run.jar` built for Postgres  
- **Expected:** Document that local smoke uses `quarkus:dev`, not prod jar + H2

### DEF-FE-001 — TypeScript error: `Button` has no `size` prop  
- **Severity:** **Major**  
- **Story:** STORY-006 (payments UI), STORY-QA-003  
- **Steps:** `cd web; npx tsc --noEmit`  
- **Expected:** Clean compile  
- **Actual:** `web/src/app/payments/payments-client.tsx:233` — `size="sm"` not in `Button` props  
- **Fix:** Remove `size` or extend `Button` API

### DEF-FE-002 — Settings health / rewrites default to port 8080  
- **Severity:** **Minor** (local DX when Apache holds 8080)  
- **Story:** STORY-021 / onboarding  
- **Evidence:** `next.config.ts` `BACKEND_URL ?? http://localhost:8080`; Settings copy mentions 8080  
- **Expected:** Document override `BACKEND_URL=http://localhost:8082` for concurrent Apache

### Residual product gaps (story backlog — not new code regressions)
Confirmed still open or only partially addressed by WIP (see story QA notes):  
STORY-003 (RBAC/OIDC), STORY-004 (Quarkus LTS), STORY-007 (credit note full AR proof), STORY-009 (webhook delivery), STORY-010 (FX allocation), STORY-011 (qty/tax lines), STORY-012 (audit actors), STORY-016 (ONBOARDING drift), etc.

---

## Story linkage (quick)

| Defect | Stories |
|--------|---------|
| DEF-BE-001, DEF-BE-003 | STORY-QA-001 |
| DEF-BE-002 | STORY-005, STORY-006 |
| DEF-BE-004 | STORY-001, STORY-002 |
| DEF-BE-005 | STORY-001 |
| DEF-FE-001 | STORY-006, STORY-QA-003 |
| DEF-FE-002 | STORY-021, STORY-QA-004 |

---

## Recommendations for Engineering

1. **Land WIP in atomic commits** with `mvn test` green; ban UTF-8 BOM in editor/agent tooling.  
2. Fix `PaymentResource` to a **single `@Inject` constructor**.  
3. Fix FE `size="sm"` (DEF-FE-001) before merge.  
4. Update application unit tests for new cheque/invoice constructors.  
5. Add smoke cases: blocked customer 409, payment list/get/reverse, cheque clear `paymentId` not null.  
6. Keep local API on **8082** when 8080 is busy; set `BACKEND_URL` for Next.

---

## Sign-off

| Gate | Result |
|------|--------|
| Baseline backend tests | **PASS** (755) |
| Final API smoke | **PASS** (27/27) |
| Frontend typecheck | **FAIL** (DEF-FE-001) |
| Production readiness of Wave A stories | **Not ready** — P0 stories still In Progress / residual gaps; WIP improves 001/002/005/006 but not fully accepted |

**Overall QA verdict:** Core demo path (customer → invoice → payment allocate → cheque lifecycle → aging) works on a **stable** `quarkus:dev` instance. **Do not ship** until Wave A P0 stories complete AC, FE tsc is green, CDI constructor is fixed, and CI `mvn test` is green under final WIP.

*Servers started by QA were stopped after this report.*
