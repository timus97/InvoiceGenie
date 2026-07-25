# Project Status — InvoiceGenie (as of 2026-07-26)

> **Authoritative current status.** Older audits under `docs/PRODUCTION_READINESS.md` may be stale.

## Runtime (local)

| Service | URL | Notes |
|---------|-----|--------|
| Web console | http://localhost:3000 | Next.js BFF |
| API | http://localhost:8082 | Quarkus dev + Postgres |
| Adminer | http://localhost:8081 | SQL console |
| Postgres | localhost:5432 | DB invoicegenie, user ar |
| Bootstrap login | admin@invoicegenie.local / Admin123! | Change after first use |

## Completed features

- Multi-tenant AR core (customers, invoices, payments, cheques, credit notes, aging, ledger, FX, tenants, statements)
- Auth: email/password, BCrypt, JWT 15m + refresh 7d, Users admin UI, RBAC
- Notifications: issue / pre-due / dunning; Email+WhatsApp logging adapters; prefs; policy; history; High QA fixes
- Platform: Flyway V1-V12, webhooks, outbox, audit, Docker Compose, AWS/TLS docs

## Pending for production

| Item | Notes |
|------|--------|
| Real SMTP/SES | Fail-closed stub only |
| Meta WhatsApp API | Fail-closed stub only |
| OIDC/SSO | Deferred |
| Bounce webhooks | Not built |
| Quiet hours, unsubscribe, PDF, i18n | Backlog |
| QA-016 audit stream for notify policy | Deferred |
| Schema CHAR vs VARCHAR warnings | Non-fatal |

## Doc map

- README.md — sales + quick start
- docs/PROJECT_STATUS.md — this file
- docs/ONBOARDING.md, SCHEMA.md, FEATURE_PRIORITY_BACKLOG.md
- docs/notifications/*
- docs/aws/*, docs/deploy/*