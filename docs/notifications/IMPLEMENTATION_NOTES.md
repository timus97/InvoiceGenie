# Implementation Notes — Customer Notifications (P0 MVP)

**Date:** 2026-07-25  
**Status:** Implemented (logging providers)

## What was built

### Backend
- **Flyway V11** (`ar-bootstrap/.../V11__notifications.sql`): templates, preferences, policy, notification queue, attempts; RLS + indexes; seed EMAIL templates for `INVOICE_ISSUED`, `PAYMENT_REMINDER`, `DUNNING_NOTICE`; seed policy for demo tenant `00000000-0000-0000-0000-000000000001`.
- **Domain** (`ar-domain/.../model/notification/`): enums, aggregates, idempotency keys, `{{var}}` template renderer, repository ports.
- **Application**: `NotificationEnqueueService` (policy/prefs/template/idempotency/SKIPPED reasons), use cases for history/manual send, preferences, policy.
- **Persistence**: JPA entities + adapters.
- **Messaging**:
  - `LoggingEmailSender` / `LoggingWhatsAppSender` (default)
  - `SmtpEmailSender` stub when `email.provider=smtp`
  - `NotificationDispatchWorker` (scheduled, exponential backoff retries)
  - `PaymentReminderJob` (pre-due days from tenant policy)
  - `NotificationOutboxBridge` hooked from `OutboxWorker` for `InvoiceIssued` + `DunningNotice`
- **REST** (`NotificationResource`): list/get/attempts/send, customer preferences, notification policy with `@RequireRoles`.
- **CDI** wired in `ArApplication`; config under `invoicegenie.notifications.*` in `application.yml`.

### Frontend
- API client: `web/src/lib/api/notifications.ts` + `paths.ts`
- History page: `/notifications` + sidebar link
- Invoice detail: **Send notification (email)** button
- Customer detail: notification preferences card
- Settings: notification policy section (TENANT_ADMIN)

### Tests
- `NotificationTemplateRendererTest`
- `NotificationIdempotencyKeysTest`
- `NotificationEnqueueServiceTest` (opt-out skip + idempotency + happy path)

## How to demo

1. Start Postgres + backend:
   ```bash
   docker compose up -d postgres
   mvn -pl ar-bootstrap -am quarkus:dev -Dquarkus.profile=dev
   ```
2. Start web: `cd web && npm run dev`
3. Login: `admin@invoicegenie.local` / `Admin123!` (tenant demo UUID).
4. Ensure a customer has an **email** set.
5. Create + **Issue** an invoice → outbox worker enqueues `INVOICE_ISSUED` → dispatch worker logs:
   `[EMAIL-LOG] id=... to=... subject=...`
6. Open **Notifications** in the sidebar for history (status PENDING → SENT).
7. On invoice detail, click **Send notification (email)** (same INVOICE_ISSUED key is idempotent — returns existing row).
8. Customer detail → toggle Email off → save prefs → next automated send is `SKIPPED` / `OPTED_OUT`.
9. Settings (TENANT_ADMIN) → Notification policy → change pre-due days / channels.

### API smoke (with JWT or X-API-Key `dev-local-key`)

```http
GET  /api/v1/notifications
POST /api/v1/notifications/send
     { "invoiceId": "<uuid>", "eventType": "INVOICE_ISSUED", "channels": ["EMAIL"], "force": true }
GET  /api/v1/customers/{id}/notification-preferences
PUT  /api/v1/notification-policy
```

## Config knobs

| Env | Default |
|-----|---------|
| `INVOICEGENIE_NOTIFICATIONS_ENABLED` | true |
| `INVOICEGENIE_NOTIFICATIONS_EMAIL_ENABLED` | true |
| `INVOICEGENIE_NOTIFICATIONS_WHATSAPP_ENABLED` | false |
| `INVOICEGENIE_NOTIFICATIONS_EMAIL_PROVIDER` | logging |
| `INVOICEGENIE_NOTIFICATIONS_PRE_DUE_DAYS` | 3 |
| `INVOICEGENIE_SMTP_*` | empty (smtp stub needs host) |

## QA fixes applied (2026-07-26)

All High + most Medium/Low findings from `QA_SECURITY_FINDINGS.md`:

- Recoverable **SKIPPED** rows can be re-enqueued after contact/policy fix
- **`force`** only overrides event auto-flags (not master/channel disable); invoice UI uses `force: false`
- **PII-redacted** dispatch logs (`log-payloads=false` default)
- **claimDue** with `FOR UPDATE SKIP LOCKED` + short transactions
- Invoice **404**, status guard, reminder catch-up, destination validation, rate limit, UUID validation
- SMTP/Meta **fail closed**; WhatsApp templates seeded (V12)

See `QA_FIX_STATUS.md`.

## Incomplete / P1 follow-ups

- Real SMTP (Jakarta Mail) and Meta WhatsApp Cloud API HTTP client
- Provider delivery webhooks / bounce handling
- Quiet hours, unsubscribe links, PDF attach, i18n, channel fallback
- Full audit-stream events for policy/pref/manual send (STORY-QA-NOTIFY-016 deferred)

## RLS note (workers)

`findDue` is cross-tenant. Migration comments document that table owner (`ar`) bypasses RLS unless `FORCE ROW LEVEL SECURITY` is enabled. If FORCE is turned on later, grant a worker bypass role or add a due-dispatch policy.