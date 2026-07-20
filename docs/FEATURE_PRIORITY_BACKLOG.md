# Feature Priority Backlog — InvoiceGenie

> **Audience:** Product, eng leads, and implementers prioritizing work after onboarding.  
> **Generated:** 2026-07-20  
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
| Invoices | Strong | Strong | Strong | JPA | Good | Always issues on create (no pure DRAFT API) |
| Payments + allocation | Strong | Strong | Strong | JPA | Good | Idempotency DB-backed in adapter |
| Cheques | Good | Partial | Hybrid | JPA | Partial | Unfiltered list returns empty |
| Aging | Good | Wired | Wired | via invoices | Partial | ONBOARDING outdated (endpoint no longer stub) |
| Credit notes | Good | Present | Present | JPA | Partial | Early-payment path |
| Ledger / GL | Rules exist | Thin | Present | JPA entity exists | Partial | Main AR flows do not auto-post journals |
| Outbox | N/A | Port | Admin API | JPA | — | Write path OK; Kafka emit optional/stub |
| AuthN/AuthZ | — | — | Header-only tenant | — | Tenant override | **No real auth** |
| Multi-tenant RLS | — | — | Filter sets GUC | App filter always | — | Pooling/RLS hardening incomplete |
| AP / GL modules | — | — | — | — | — | Designed for later; not started |

---

## P0 — Production blockers & security

| ID | Item | Type | Why it matters | Suggested work | Status |
|----|------|------|----------------|----------------|--------|
| P0-01 | **Authentication & authorization** | Missing feature | Any client knowing a tenant UUID can call the API. | API-key + HS256 JWT gate (`AuthFilter`); prod enables security; UI sends `X-API-Key` when configured. Full OIDC/RBAC still future. | **Partial (this PR)** |
| P0-02 | **Secrets & config for production** | Incomplete | Default Postgres password in compose/yml. | Env-based datasource + security secrets; `.env.example`; compose requires passwords. | **Done (this PR)** |
| P0-03 | **Quarkus platform still on EOL LTS** | Vulnerable dependency / debt | `3.8.6.1` last 3.8 patch; EOL. | See `docs/QUARKUS_LTS_MIGRATION.md` — dedicated follow-up PR. | **Planned** |
| P0-04 | **Production container image** | Incomplete | Arena Dockerfile not prod. | `Dockerfile.prod` multi-stage JVM; compose uses it; arena kept as `Dockerfile.arena`. | **Done (this PR)** |
| P0-05 | **Schema migration strategy** | Incomplete | Manual SQL only. | Flyway `V1`/`V2` under `db/migration`; migrate-at-start for Postgres/prod. | **Done (this PR)** |
| P0-06 | **Dependency security scanning in CI** | Missing process | Local scripts only. | `.github/workflows/ci.yml` + `security.yml` (OWASP + npm audit). | **Done (this PR)** |
| P0-07 | **TLS / network hardening** | Missing | HTTP only; Swagger open. | `docs/deploy/nginx-tls.conf`; prod disables Swagger/OpenAPI. | **Partial (this PR)** |

---

## P1 — Incomplete / incorrect product features

| ID | Item | Type | Evidence | Suggested work |
|----|------|------|----------|----------------|
| P1-01 | **Cheque list without status filter is empty** | Incomplete | `ChequeApplicationService.list` returns `List.of()` when status is null — “No full-list endpoint on repository”. | Add `findByTenant` (paginated) on `ChequeRepository` + adapter + wire list API/UI. |
| P1-02 | **Ledger not posted from main AR flows** | Incomplete | Domain `LedgerService` rules exist; invoice issue / payment / write-off paths do not automatically post durable entries. | Post double-entry journals in application services (or domain events → ledger projector); cover with integration tests. |
| P1-03 | **Kafka outbox publish path** | Incomplete / stub | Outbox rows written; `outbox.kafka-enabled=false`; `OutboxKafkaSender` interface only; SmallRye connectors commented. | Provide `OutboxKafkaSender` bean, enable messaging config, integration test with Testcontainers Kafka. |
| P1-04 | **Invoice pure DRAFT create** | Incomplete | Lifecycle supports DRAFT, but create path always issues. | Split `POST /invoices` draft create vs issue; UI draft editor. |
| P1-05 | **Idempotency on invoice create** | Incomplete | Header accepted conceptually; allocation has store; invoice create not keyed. | Reuse `IdempotencyStore` for POST invoice/payment create. |
| P1-06 | **SQL ↔ JPA alignment** | Refactor / bug risk | ONBOARDING §12: currency column naming, `customer_id` vs `customer_ref`, payment version, etc. | Single source of truth via migrations + entity audit; add schema validation tests against Postgres. |
| P1-07 | **RLS effectiveness under pooling** | Incomplete hardening | `DbTenantContext` sets GUC; pooling may not keep SET LOCAL on same connection without interceptor. | Hibernate statement inspector or connection customizer; prove isolation with multi-tenant integration tests. |
| P1-08 | **Invoice version snapshots** | Missing feature | Table `ar_invoice_version` in schema; app path thin/absent. | Persist immutable JSONB snapshot on each version change. |
| P1-09 | **Exchange rates / multi-currency conversion** | Missing feature | Schema `ar_exchange_rate`; Money same-currency arithmetic only. | Rate table API + conversion for reporting/base currency. |
| P1-10 | **Tenant registry API** | Missing feature | `ar_tenant` table; no first-class tenant admin API beyond header. | Tenant CRUD/bootstrap for onboarding new orgs. |

---

## P2 — Architecture refactoring & quality

| ID | Item | Type | Evidence | Suggested work |
|----|------|------|----------|----------------|
| P2-01 | **Hexagonal purity for hybrid resources** | Refactor | Cheques/aging/credit notes historically mixed domain-from-REST; partially improved with use cases — audit remaining producers/resources. | Ensure all REST → application inbound ports only; domain free of HTTP concerns. |
| P2-02 | **API adapter → messaging dependency** | Boundary smell | Outbox admin endpoints pull messaging into API module (ONBOARDING). | Move outbox admin to bootstrap or ops module; keep adapters unidirectional. |
| P2-03 | **Remove unused SQLite JDBC extension** | Cleanup | ~~`quarkus-jdbc-sqlite` in persistence~~ **Removed** (2026-07-20). Legacy `%sqlite` profile remains as H2 alias only. | Optional follow-up: delete `%sqlite` profile after one release cycle. |
| P2-04 | **Dedicated API layer tests** | Missing tests | ONBOARDING: `ar-adapter-api` historically thin on tests (unit tests exist for some; expand resource contract tests). | REST contract tests with RestAssured per resource; OpenAPI snapshot tests. |
| P2-05 | **Observability** | Incomplete | Health endpoint present; no metrics/tracing dashboards documented. | Micrometer + OpenTelemetry; structured JSON logs; RED metrics for AR APIs. |
| P2-06 | **Idempotency store operationalization** | Incomplete | DB adapter exists; ensure migrations + TTL cleanup job. | SQL migration for idempotency table; retention policy. |
| P2-07 | **ONBOARDING doc drift** | Docs debt | Aging “stub”, ledger “in-memory”, RLS “not set” partially outdated vs current code. | Refresh §4–5 and §12 after each maturity milestone. |
| P2-08 | **gRPC / Kafka consumers** | Missing (diagram only) | Architecture diagram mentions gRPC/Kafka consumer; not implemented. | Defer until product needs event-driven ingest. |
| P2-09 | **Frontend production auth** | Incomplete | Settings allow tenant override; fine for demos, unsafe for prod. | SSO login, hide tenant switcher when not admin. |
| P2-10 | **Coverage gate enforcement in CI** | Process | JaCoCo 80% configured; ensure CI runs `verify` not only `test`. | Pipeline: `mvn verify` + coverage scripts. |

---

## P3 — Future product / scale

| ID | Item | Type | Notes |
|----|------|------|-------|
| P3-01 | Accounts Payable (AP) module | Missing module | Parent design mentions AR → AP/GL expansion. |
| P3-02 | Full General Ledger module | Missing module | Chart of accounts as data (not only enum); period close. |
| P3-03 | Native image (GraalVM) build | Optional | Faster cold start for K8s scale-to-zero. |
| P3-04 | Multi-region / read replicas | Scale | Reporting path separation. |
| P3-05 | Advanced collections workflows | Feature | Dunning, statements, dispute management. |
| P3-06 | Webhooks for customers | Feature | Beyond internal outbox/Kafka. |
| P3-07 | Audit log UI & export | Feature | Compliance export (CSV/PDF). |
| P3-08 | Playwright E2E suite for `web/` | Quality | Smoke critical paths against docker-compose. |

---

## Recommended delivery order (PR plan sketch)

```
Wave 1 (P0): Auth + secrets + prod Dockerfile + Flyway schema baseline
     → Security scan in CI + Quarkus LTS migration spike

Wave 2 (P1): Cheque list, ledger posting, Kafka sender, DRAFT invoices,
     → Idempotency expansion, RLS proof tests

Wave 3 (P2): Hexagon cleanup, observability, doc sync, drop sqlite legacy

Wave 4 (P3): AP/GL product modules and scale features
```

---

## Recently addressed (this security pass)

| Item | Status |
|------|--------|
| OWASP Dependency-Check Maven profile (`-Psecurity-scan`) | Added |
| `scripts/security-scan.ps1` / `.sh` | Added |
| npm `audit` / `audit:ci` scripts + `postcss` override (GHSA-qx2v-qp2m-jg93) | Added |
| Quarkus BOM `3.8.6` → `3.8.6.1` (last 3.8 security line) | Applied |
| React `19.1.0` → `19.1.8` | Applied |
| Removed unused `quarkus-jdbc-sqlite` | Applied |
| AssertJ / Mockito test deps bumped | Applied |

---

## Maintenance

| When you… | Update… |
|-----------|---------|
| Ship a missing feature above | Mark row **Done** with PR link |
| Discover new debt | Add ID in the matching priority table |
| Change production posture | Sync `docs/PRODUCTION_READINESS.md` |

*Living document — complement to `docs/ONBOARDING.md` and `README.md`.*
