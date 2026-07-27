# Project Status — InvoiceGenie (as of 2026-07-27)

> **Authoritative current status.** Older audits under `docs/PRODUCTION_READINESS.md` may be stale.
> Production Path program: `docs/PRODUCTION_PATH_STORIES.md`.

## Runtime (local)

| Service | URL | Notes |
|---------|-----|--------|
| Web console | http://localhost:3000 | Next.js BFF |
| API | http://localhost:8082 | Quarkus dev + Postgres (confirm port; containers often 8080) |
| Adminer | http://localhost:8081 | SQL console |
| Postgres | localhost:5432 | DB invoicegenie, user ar |
| Bootstrap login | admin@invoicegenie.local / Admin123! | Change after first use |

## Completed features

- Multi-tenant AR core (customers, invoices, payments, cheques, credit notes, aging, ledger, FX, tenants, statements)
- Auth: email/password, BCrypt, JWT 15m + refresh 7d, Users admin UI, RBAC
- Notifications: issue / pre-due / dunning; Email+WhatsApp **logging** adapters; prefs; policy; history; High QA fixes
- Platform: Flyway V1-V12, webhooks, outbox, audit, Docker Compose, AWS/TLS docs

## Production Path (PP) — status

Program in progress (2026-07-27). Ops/docs stream **PP-040…PP-043** deliverables are **Done** in-repo (no live AWS deploy required).

| Story | Area | Status |
|-------|------|--------|
| PP-001 | Real SMTP transport | Planned / in progress (sender fail-closed stub today) |
| PP-002 | Meta WhatsApp client | Planned / in progress (fail-closed stub today) |
| PP-003…PP-016 | Notify polish, PDF, metrics, etc. | Planned |
| PP-020…PP-026 | OIDC optional, pagination, period close, etc. | Planned |
| PP-030…PP-036 | Frontend Production Path | Planned |
| **PP-040** | Email deliverability runbook | **Done** — `docs/deploy/EMAIL_DELIVERABILITY.md` |
| **PP-041** | WhatsApp Meta setup runbook | **Done** — `docs/deploy/WHATSAPP_META_SETUP.md` |
| **PP-042** | Production path / staging runbook | **Done** — `docs/deploy/PRODUCTION_PATH_RUNBOOK.md` |
| **PP-043** | PROJECT_STATUS + env/docs sync | **Done** — this file, `.env.example`, notify notes addendum |
| PP-050…PP-052 | QA after merge | Pending |

## Pending for production

| Item | Notes |
|------|--------|
| Real SMTP/SES | Fail-closed stub; runbook ready (`EMAIL_DELIVERABILITY.md`) |
| Meta WhatsApp API | Fail-closed stub; runbook ready (`WHATSAPP_META_SETUP.md`) |
| OIDC/SSO | Deferred / PP-020 optional path |
| Bounce webhooks | Not built (PP-003) |
| Quiet hours, unsubscribe, PDF, i18n | Backlog / PP-010+ |
| QA-016 audit stream for notify policy | Deferred / PP-016 |
| Schema CHAR vs VARCHAR warnings | Non-fatal |
| Live AWS deploy | Ops environment; IaC + checklist exist under `docs/aws/` |

## Doc map

- README.md — sales + quick start
- docs/PROJECT_STATUS.md — this file
- docs/PRODUCTION_PATH_STORIES.md — active PP story catalog
- docs/ONBOARDING.md, SCHEMA.md, FEATURE_PRIORITY_BACKLOG.md
- docs/notifications/*
- docs/aws/* — hosting design, deployment checklist, terraform
- docs/deploy/* — TLS edge, **email deliverability**, **WhatsApp Meta**, **production path runbook**
