# Project Status — InvoiceGenie

**Updated:** 2026-07-27  
**Branch:** `Development` (Production Path integrated)

## What InvoiceGenie is

Multi-tenant **Accounts Receivable** platform: customers, invoices, payments/cheques, aging, ledger, webhooks, audit, and customer notifications (Email + WhatsApp pipeline) with an operator console.

## Local URLs

| Service | URL |
|---------|-----|
| Console | http://localhost:3000 |
| API | http://localhost:8082 |
| OpenAPI | http://localhost:8082/q/swagger-ui/ |
| Adminer | http://localhost:8081 |
| Login | `admin@invoicegenie.local` / `Admin123!` |
| Demo tenant | `00000000-0000-0000-0000-000000000001` |

## Shipped capabilities

- AR core: customers, invoices (draft/issue/write-off), payments (FIFO/manual/reverse/unallocate), cheques, credit notes, aging, statements
- Security: login + JWT/refresh, RBAC, optional OIDC JWKS (`oidc` / `hybrid-oidc`)
- Notifications: auto on issue, pre-due, dunning, statement send; quiet hours; unsubscribe; metrics; template preview
- Delivery: **logging** (demo), **SMTP** (Jakarta Mail), **Meta WhatsApp**; bounce suppressions; channel fallback
- PDF invoice & statement; posting periods; webhook delivery + redrive; cursor pagination; optional FX allocation
- Flyway V1–V15; Docker Compose; AWS IaC docs

## Configure delivery

| Goal | Env |
|------|-----|
| Demo email (no inbox) | `INVOICEGENIE_NOTIFICATIONS_EMAIL_PROVIDER=logging` |
| Real email | `=smtp` + `INVOICEGENIE_SMTP_*` (see `deploy/EMAIL_DELIVERABILITY.md`) |
| WhatsApp Meta | `INVOICEGENIE_NOTIFICATIONS_WHATSAPP_PROVIDER=meta` + token/phone id |

## Still ops / product (not code gaps)

- Live cloud staging + TLS certs
- SES domain / Meta template approval
- Load test + pilot tenants
- Deferred modules: AP, full GL product, multi-region, customer payment portal

## Docs map

See [docs/README.md](README.md). Demo: [DEMO.md](DEMO.md). Next PO review: [PO_NEXT_REVIEW.md](PO_NEXT_REVIEW.md).
