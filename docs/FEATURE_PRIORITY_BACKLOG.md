# Feature Priority Backlog — InvoiceGenie

> **Audience:** Product, eng leads, and implementers prioritizing work after onboarding.  
> **Generated:** 2026-07-20  
> **Last audited:** 2026-07-24  
> **Sources:** Code audit of modules under this repo, `docs/ONBOARDING.md` §12, README gaps, and production-readiness review.  
> **How to use:** Pick from **P0** first for production readiness; **P1** for AR completeness; **P2+** for scale/platform.

---

## Priority legend

| Priority | Meaning | Target window |
|----------|---------|----------------|
| **P0** | Blocks safe production deploy or creates critical risk | Immediate |
| **P1** | Core AR capability incomplete / incorrect for real customers | Next sprint(s) |
| **P2** | Architecture purity, DX, or important but non-blocking gaps | Near term |
| **P3** | Nice-to-have, future modules, polish | Backlog |

---

## Snapshot: maturity by capability

| Capability | Domain rules | Application use cases | REST | Persistence | UI | Notes |
|------------|-------------|----------------------|------|-------------|-----|-------|
| Customers | Good | Good | Good | JPA | Good | Solid CRUD + block/credit |
| Invoices | Strong | Strong | Strong | JPA | Good | DRAFT via `issueImmediately=false`; versions persisted |
| Payments + allocation | Strong | Strong | Strong | JPA | Good | Idempotency DB-backed + TTL cleanup |
| Cheques | Good | Good | Good | JPA | Good | Unfiltered list returns all tenant cheques |
| Aging | Good | Wired | Wired | via invoices | Good | Live report endpoint |
| Credit notes | Good | Present | Present | JPA | Good | Early-payment path |
| Ledger / GL | Rules exist | Wired | Present | JPA | Good | Issue/payment/write-off/cheque post durable journals |
| Outbox | N/A | Port | Admin API | JPA | — | Kafka sender bean present; enable with env |
| AuthN/AuthZ | — | — | API-key + JWT | — | API key support | Full OIDC/RBAC still future |
| Multi-tenant RLS | — | — | Filter + Agroal interceptor | App filter + GUC | — | Pool acquire/return rebinds tenant |
| Exchange rates | Present | Present | Present | JPA | Present | Conversion + inverse rate |
| Tenant registry | Present | Present | Present | JPA | Present | CRUD + activate/suspend |
| Webhooks | Present | Present | Present | JPA | Present | Customer callback subscriptions |
| Audit export | Present | Present | Present | JPA | Present | List + CSV export |
| AP / full GL modules | — | — | — | — | — | Intentionally deferred product modules |

---

## P0 — Production blockers & security

| ID | Item | Type | Why it matters | Suggested work | Status |
|----|------|------|----------------|----------------|--------|
| P0-01 | **Authentication & authorization** | Missing feature | Any client knowing a tenant UUID can call the API. | API-key + HS256 JWT gate (`AuthFilter`); prod enables security; UI sends `X-API-Key` when configured. Full OIDC/RBAC still future. | **Partial** — gate production-ready; OIDC/RBAC deferred |
| P0-02 | **Secrets & config for production** | Incomplete | Default Postgres password in compose/yml. | Env-based datasource + security secrets; `.env.example`; compose requires passwords. | **Done** |
| P0-03 | **Quarkus platform still on EOL LTS** | Vulnerable dependency / debt | `3.8.6.1` last 3.8 patch; EOL. | See `docs/QUARKUS_LTS_MIGRATION.md` — dedicated migration PR (REST extension renames). | **Planned** |
| P0-04 | **Production container image** | Incomplete | Arena Dockerfile not prod. | `Dockerfile.prod` multi-stage JVM; compose uses it; arena kept as `Dockerfile.arena`. | **Done** |
| P0-05 | **Schema migration strategy** | Incomplete | Manual SQL only. | Flyway `V1`–`V6` under `db/migration`; migrate-at-start for Postgres/prod. | **Done** |
| P0-06 | **Dependency security scanning in CI** | Missing process | Local scripts only. | `.github/workflows/ci.yml` + `security.yml` (OWASP + npm audit). | **Done** |
| P0-07 | **TLS / network hardening** | Missing | HTTP only; Swagger open. | `docs/deploy/nginx-tls.conf`; prod disables Swagger/OpenAPI; edge TLS ops-owned. | **Partial** — docs + prod OpenAPI off; edge TLS required |

---

## P1 — Incomplete / incorrect product features

| ID | Item | Type | Evidence | Suggested work | Status |
|----|------|------|----------|----------------|--------|
| P1-01 | **Cheque list without status filter is empty** | Incomplete | Was returning empty list. | `findByTenant` on repository + adapter + list API/UI. | **Done** |
| P1-02 | **Ledger not posted from main AR flows** | Incomplete | Domain rules without durable posts. | Post journals on issue / payment / write-off / cheque clear-bounce. | **Done** |
| P1-03 | **Kafka outbox publish path** | Incomplete / stub | Interface only historically. | `SmallRyeOutboxKafkaSender` bean + env `OUTBOX_KAFKA_ENABLED` + bootstrap servers. | **Done** |
| P1-04 | **Invoice pure DRAFT create** | Incomplete | Always issued. | `issueImmediately=false` API + UI checkbox for draft create. | **Done** |
| P1-05 | **Idempotency on invoice create** | Incomplete | Not keyed. | `Idempotency-Key` on invoice create (and payments). | **Done** |
| P1-06 | **SQL ↔ JPA alignment** | Refactor / bug risk | Column naming drift. | Flyway `V3`–`V5` alignment + entities; dual-path reduced. | **Done** (ongoing vigilance) |
| P1-07 | **RLS effectiveness under pooling** | Incomplete hardening | GUC only on request filter. | `TenantConnectionInterceptor` (Agroal) rebinds on acquire/return. | **Done** |
| P1-08 | **Invoice version snapshots** | Missing feature | Table only. | Persist JSONB/text snapshot on create/lifecycle; `GET /invoices/{id}/versions`. | **Done** |
| P1-09 | **Exchange rates / multi-currency conversion** | Missing feature | Schema only. | Rate CRUD + conversion service + UI. | **Done** |
| P1-10 | **Tenant registry API** | Missing feature | Seed only. | Tenant CRUD + activate/suspend + UI. | **Done** |

---

## P2 — Architecture refactoring & quality

| ID | Item | Type | Evidence | Suggested work | Status |
|----|------|------|----------|----------------|--------|
| P2-01 | **Hexagonal purity for hybrid resources** | Refactor | REST mixed domain historically. | All REST → inbound ports only. | **Done** |
| P2-02 | **API adapter → messaging dependency** | Boundary smell | Outbox in API module. | Outbox admin in `ar-bootstrap` ops. | **Done** |
| P2-03 | **Remove unused SQLite JDBC extension** | Cleanup | JDBC removed; `%sqlite` alias remains. | Optional: delete `%sqlite` after release cycle. | **Partial** (dep gone) |
| P2-04 | **Dedicated API layer tests** | Missing tests | Thin historically. | Resource unit tests for all REST resources. | **Done** (unit contract style) |
| P2-05 | **Observability** | Incomplete | Health only. | Micrometer Prometheus `/q/metrics`, OpenTelemetry (opt-in), JSON logs in prod. | **Done** |
| P2-06 | **Idempotency store operationalization** | Incomplete | No TTL job. | Flyway + `IdempotencyCleanupJob` retention cron. | **Done** |
| P2-07 | **ONBOARDING doc drift** | Docs debt | Stale sections. | Refresh after maturity milestones (see maintenance). | **Partial** — backlog snapshot refreshed 2026-07-24 |
| P2-08 | **gRPC / Kafka consumers** | Missing (diagram only) | Publisher side only. | Defer until product needs event-driven ingest. | **Deferred** |
| P2-09 | **Frontend production auth** | Incomplete | Tenant switcher. | API key header + hide override when `NEXT_PUBLIC_ALLOW_TENANT_OVERRIDE=false`. SSO future. | **Partial** |
| P2-10 | **Coverage gate enforcement in CI** | Process | JaCoCo on verify only. | CI runs `mvn verify` (JaCoCo check 80%). | **Done** |

---

## P3 — Future product / scale

| ID | Item | Type | Notes | Status |
|----|------|------|-------|--------|
| P3-01 | Accounts Payable (AP) module | Missing module | Parent design mentions AR → AP/GL expansion. | **Deferred** (new product module) |
| P3-02 | Full General Ledger module | Missing module | Chart of accounts as data; period close. | **Deferred** (AR ledger subset live) |
| P3-03 | Native image (GraalVM) build | Optional | Faster cold start for K8s scale-to-zero. | **Deferred** |
| P3-04 | Multi-region / read replicas | Scale | Reporting path separation. | **Deferred** |
| P3-05 | Advanced collections workflows | Feature | Dunning, statements, dispute management. | **Deferred** |
| P3-06 | Webhooks for customers | Feature | Beyond internal outbox/Kafka. | **Done** — `/api/v1/webhooks` + UI |
| P3-07 | Audit log UI & export | Feature | Compliance export (CSV/PDF). | **Done** — `/api/v1/audit` + CSV + UI |
| P3-08 | Playwright E2E suite for `web/` | Quality | Smoke critical paths. | **Done** — `web/e2e` + Playwright config |

---

## Recommended delivery order (PR plan sketch)

```
Wave 1 (P0): Auth + secrets + prod Dockerfile + Flyway schema baseline  ✅
     → Security scan in CI + Quarkus LTS migration spike (P0-03 remaining)

Wave 2 (P1): Cheque list, ledger posting, Kafka sender, DRAFT invoices,  ✅
     → Idempotency, RLS pool interceptor, versions, FX, tenants           ✅

Wave 3 (P2): Hexagon cleanup, observability, coverage gate, cleanup jobs ✅
     → Doc sync / OIDC / drop sqlite alias residual

Wave 4 (P3): Webhooks + audit UI/export + Playwright smoke               ✅
     → AP/GL product modules and scale features (deferred)
```

---

## Recently addressed (2026-07-24 completion pass)

| Item | Status |
|------|--------|
| Tenant registry API + UI (P1-10) | Done |
| Exchange rates + conversion (P1-09) | Done |
| Invoice version snapshots (P1-08) | Done |
| OutboxKafkaSender CDI bean (P1-03) | Done |
| Agroal RLS connection interceptor (P1-07) | Done |
| Micrometer / OTel / JSON logs (P2-05) | Done |
| Idempotency TTL cleanup job (P2-06) | Done |
| CI `mvn verify` coverage gate (P2-10) | Done |
| Draft invoice UI (P1-04) | Done |
| Webhooks API + UI (P3-06) | Done |
| Audit list + CSV export + UI (P3-07) | Done |
| Playwright smoke suite (P3-08) | Done |
| Flyway V6 webhooks | Done |

---

## Remaining open items (honest residual)

| ID | Residual |
|----|----------|
| **P0-03** | Migrate Quarkus `3.8.6.1` → supported LTS (3.27/3.33) — dedicated PR |
| **P0-01** | OIDC provider + RBAC roles beyond API-key/JWT gate |
| **P0-07** | Wire TLS into compose/K8s edge (ops); nginx sample already in docs |
| **P2-03** | Delete `%sqlite` profile alias after notice period |
| **P2-07** | Full ONBOARDING.md rewrite for sections still naming stubs |
| **P2-08** | Kafka/gRPC consumers when product requires inbound events |
| **P2-09** | SSO login UX |
| **P3-01..05** | New product modules / platform scale |

---

## Maintenance

| When you… | Update… |
|-----------|---------|
| Ship a missing feature above | Mark row **Done** with PR link |
| Discover new debt | Add ID in the matching priority table |
| Change production posture | Sync `docs/PRODUCTION_READINESS.md` |

*Living document — complement to `docs/ONBOARDING.md` and `README.md`.*