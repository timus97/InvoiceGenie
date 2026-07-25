# Product Owner: Customer Notification System (Email + WhatsApp)

**Product:** InvoiceGenie multi-tenant AR  
**Status:** Ready for architecture  
**Date:** 2026-07-25

## Vision

Proactively contact customers about invoices (issue + payment reminders + dunning) via **Email** and **WhatsApp**, with tenant isolation, consent, audit, and delivery status.

## Gap today

- Dunning emits `DunningNotice` outbox events only — **no customer delivery**
- Customer has email/phone but no preferences/consent
- Webhooks are for tenant systems, not end customers

## P0 MVP stories

| ID | Title |
|----|-------|
| STORY-NOTIFY-001 | Domain model & multi-tenant storage (notification, attempt, template, preference) |
| STORY-NOTIFY-002 | Customer notification preferences & opt-out |
| STORY-NOTIFY-003 | Template registry & safe variable rendering |
| STORY-NOTIFY-004 | Enqueue service + idempotency |
| STORY-NOTIFY-005 | Email channel adapter (logging/SMTP/SES) |
| STORY-NOTIFY-006 | WhatsApp Cloud API adapter (templates) |
| STORY-NOTIFY-007 | Dispatch worker (retry, rate limit) |
| STORY-NOTIFY-008 | Send invoice on issue (auto) + manual send API/UI |
| STORY-NOTIFY-009 | Pre-due + dunning scheduler with de-dupe by level |
| STORY-NOTIFY-010 | Notification history API & UI |
| STORY-NOTIFY-011 | Tenant notification policy settings |
| STORY-NOTIFY-012 | RBAC / PII / secrets |
| STORY-NOTIFY-021 | Demo logging sink + docs + smoke |

## Event types (MVP)

- `INVOICE_ISSUED`
- `PAYMENT_REMINDER` (pre-due, default T-3)
- `DUNNING_NOTICE` (levels from existing dunning 30/60/90)

## Conceptual data model

```
ar_notification_template
ar_notification_preference
ar_notification_policy (or tenant settings)
ar_notification
ar_notification_attempt
```

Statuses: PENDING, QUEUED, SENDING, SENT, FAILED, CANCELLED, SKIPPED

## Product decisions (MVP)

- Async queue + worker (like webhooks)
- One send per dunning level per channel
- PDF out of MVP
- Default email provider: logging in dev
- WhatsApp: Meta template messages only
- Opt-out blocks automated sends

## Config placeholders

```
INVOICEGENIE_NOTIFICATIONS_ENABLED=true
INVOICEGENIE_NOTIFICATIONS_EMAIL_ENABLED=true
INVOICEGENIE_NOTIFICATIONS_WHATSAPP_ENABLED=false
INVOICEGENIE_NOTIFICATIONS_EMAIL_PROVIDER=logging
INVOICEGENIE_NOTIFICATIONS_PRE_DUE_DAYS=3
INVOICEGENIE_SMTP_* / INVOICEGENIE_WHATSAPP_*
```

## Bootstrap demo login

- admin@invoicegenie.local / Admin123!
- Tenant: 00000000-0000-0000-0000-000000000001

## P1 later

013 unsubscribe, 014 provider webhooks, 015 preview, 016 quiet hours, 017 statement send

## P2 later

018 PDF, 019 i18n, 020 channel fallback