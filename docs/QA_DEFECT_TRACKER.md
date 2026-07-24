# QA Defect Tracker — InvoiceGenie AR

> **Updated:** 2026-07-24 (re-test pass)  
> **Source:** `docs/QA_TEST_REPORT.md`  
> **Branch:** Development

## Status legend

| Status | Meaning |
|--------|---------|
| **OPEN** | Still reproduces or residual risk |
| **CLOSED** | Fixed and re-verified with evidence |
| **PROCESS** | Engineering process / multi-agent risk (not a single code bug) |

## Defects

| ID | Severity | Area | Summary | Status | Evidence (re-test 2026-07-24) | Stories |
|----|----------|------|---------|--------|-------------------------------|---------|
| DEF-BE-001 | Major | Build / DX | UTF-8 BOM on `.java` breaks Quarkus live reload | **CLOSED** | Full tree BOM scan: 0 files with EF BB BF | STORY-QA-001 |
| DEF-BE-002 | Major | Payments API | `PaymentResource` intermittent CDI bean create failure | **CLOSED** | `@Inject` 4-arg ctor; smoke list/create/get/reverse stable (44/44 + node smoke) | STORY-005, STORY-006, STORY-QA-002 |
| DEF-BE-003 | Major | Build / DX | Concurrent WIP incomplete `target/classes` breaks `quarkus:dev` | **OPEN** (PROCESS) | Not re-triggered this pass; multi-agent risk remains | STORY-QA-001 |
| DEF-BE-004 | Major | Tests | Application tests fail to load after constructor expansion | **CLOSED** | `mvn test` **758** tests, 0 fail, 0 error | STORY-001, STORY-002 |
| DEF-BE-005 | Minor | Invoices | Missing `dueDate` → 400 before credit/block semantics | **OPEN** | Still requires dueDate for STORY-001 API probes | STORY-001, STORY-QA-005 |
| DEF-BE-006 | Minor | Config | Unrecognized `%dev` keys `quarkus.datasource.jdbc.username/password` | **OPEN** | WARN still observed in test/dev logs | STORY-022 |
| DEF-BE-007 | Minor | Ops | Packaged jar cannot switch to H2 via runtime profile alone | **OPEN** (documented) | Documented in ONBOARDING §8 + `docs/deploy/PROD_EDGE_TLS.md`; smoke must use `quarkus:dev` | STORY-017 |
| DEF-FE-001 | Major | Payments UI | `Button` had no `size` prop → tsc fail | **CLOSED** | `Button` now has `size?: sm|md|lg`; `npx tsc --noEmit` exit 0 | STORY-006, STORY-QA-003 |
| DEF-FE-002 | Minor | Web DX | Settings/rewrites default `BACKEND_URL` → :8080 | **OPEN** | Port 8080 occupied by Apache this session; used 8082 for API | STORY-021, STORY-QA-004 |

## Wave A story verification (API-focused)

| Story | Priority | AC (API core) | Re-test |
|-------|----------|---------------|---------|
| STORY-001 | P0 | Block + credit enforce on issue | **PASS** (409 + codes; unit tests present) |
| STORY-002 | P0 | Clear links paymentId / cash apply | **PASS** (paymentId non-null); UI dialog residual |
| STORY-003 | P0 | RBAC / prod auth | **OPEN** (WIP uncommitted; not accepted) |
| STORY-004 | P0 | Quarkus LTS | **OPEN** (still 3.8.6.1) |
| STORY-005 | P1 | Payment reverse/refund | **PASS** reverse API; refund lightly covered |
| STORY-006 | P1 | Payment list/get + UI | **PASS** API; FE tsc unblocked |
| STORY-007 | P1 | Credit note AR impact | **NOT e2e asserted** this pass |
| STORY-008 | P1 | Aging customerId + overdue job | Aging 200; job not runtime-verified |

## Smoke coverage (extended)

| Case | `test-api.ps1` | `smoke-ar.mjs` |
|------|----------------|----------------|
| Health / CRUD baseline | Yes | Yes |
| Blocked customer 409 + code | Yes (section 9) | Yes |
| Credit limit 409 | Yes (section 9) | No |
| Payment list/get | Yes (section 9) | Yes |
| Payment reverse to REVERSED | Yes (section 9) | Yes |
| Cheque clear paymentId not null | Yes (section 9) | Yes |

## Latest gate numbers

| Gate | Result |
|------|--------|
| `mvn test` | 758 pass / 0 fail / 0 error |
| `npm run lint` | PASS |
| `npx tsc --noEmit` | PASS |
| API smoke | **44 PASS / 0 FAIL** |
| Node smoke | PASS |

## Sign-off

**Conditional no-ship** for production: Wave A correctness APIs for 001/002/005/006 verified green; STORY-003/004 and minor residuals block production ship.