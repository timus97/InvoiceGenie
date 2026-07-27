# Project Status — InvoiceGenie (as of 2026-07-27)

> **Authoritative current status** after Production Path integration (`feat/production-path-integration`).

## Runtime (local)

| Service | URL | Notes |
|---------|-----|--------|
| Web console | http://localhost:3000 | Next.js BFF (default API :8082) |
| API | http://localhost:8082 | Quarkus + Postgres |
| Adminer | http://localhost:8081 | SQL console |
| Bootstrap login | admin@invoicegenie.local / Admin123! | Change after first use |

## Completed features (AR core + Production Path)

- Multi-tenant AR core (customers, invoices, payments, cheques, credit notes, aging, ledger, FX, tenants, statements)
- Auth: email/password, BCrypt, JWT + refresh, RBAC, **OIDC/JWKS modes** (`oidc` / `hybrid-oidc`)
- Notifications pipeline: issue / pre-due / dunning / **statement send**
- **Real SMTP** (Jakarta Mail) + **Meta WhatsApp** + logging demo adapters
- **Bounce suppressions** + provider webhooks; **channel fallback**
- **Quiet hours**, **unsubscribe** tokens, **PDF** invoice/statement, attach-on-issue
- **Template preview**, **notification metrics**, policy/pref/send **audit**
- **Cursor pagination** (payments, notifications)
- **Cross-currency allocation** (flag `allow-fx-allocation`)
- **Posting period close** MVP + admin API
- **Webhook redrive**
- Console: metrics, preview, policy, PDF, statement send, redrive, public `/unsubscribe`
- Platform: Flyway **V1–V15**, webhooks, outbox, Docker Compose, AWS/TLS docs, production path runbooks

## Remaining for true production go-live (ops / pilot)

| Item | Notes |
|------|--------|
| Live cloud staging | Terraform present; deploy + TLS certs ops-owned |
| Email domain warm-up | SPF/DKIM/SES config in real DNS |
| Meta template approval | Business verification + approved template names |
| Load / pen-test burn-in | Staging required |
| Full FX gain/loss ledger | Lite conversion shipped; multi-currency P&L residual |
| AP / full GL / multi-region | Intentionally deferred product modules |
| Customer payment portal + PSP | Future differentiator |
| CI `mvn verify` coverage gate | Still using `mvn test` until coverage remediation sprint |

## Doc map

- `docs/PROJECT_REVIEW_STAKEHOLDER.md` — stakeholder review
- `docs/PRODUCTION_PATH_STORIES.md` — story catalog
- `docs/deploy/EMAIL_DELIVERABILITY.md`, `WHATSAPP_META_SETUP.md`, `PRODUCTION_PATH_RUNBOOK.md`, `OIDC.md`
- `docs/notifications/*`, `docs/aws/*`

## Test gate (2026-07-27)

- Unit modules: **935 tests, 0 fail / 0 error** (shared-kernel through ar-adapter-messaging)
- `ar-bootstrap` Quarkus E2E: requires local Postgres credentials (env-dependent)

