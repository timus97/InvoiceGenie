# Implementation Notes — Customer Notifications (P0 MVP)

**Date:** 2026-07-25 (ops addendum 2026-07-27)  
**Status:** Implemented (logging providers); production provider runbooks documented (PP-040…043)

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
| `INVOICEGENIE_NOTIFICATIONS_EMAIL_PROVIDER` | logging (`smtp` = fail-closed until PP-001) |
| `INVOICEGENIE_NOTIFICATIONS_WHATSAPP_PROVIDER` | logging (`meta` = fail-closed until PP-002) |
| `INVOICEGENIE_NOTIFICATIONS_PRE_DUE_DAYS` | 3 |
| `INVOICEGENIE_SMTP_*` | host=`none` until configured |
| `INVOICEGENIE_WHATSAPP_*` | token/phone-number-id=`none` until Meta setup |
| `INVOICEGENIE_PUBLIC_BASE_URL` | public HTTPS base for links/webhooks (ops) |

Full list: repo root `.env.example`.

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

---

## Addendum — Production providers (PP-040…PP-043, 2026-07-27)

Ops runbooks and env keys are documented for production path readiness **without** requiring live SES/Meta deploy in this pass.

### Provider reality check

| Provider flag | Class | Production readiness |
|---------------|-------|----------------------|
| `email.provider=logging` | `LoggingEmailSender` | Demo only — marks SENT without external mail |
| `email.provider=smtp` | `SmtpEmailSender` | **Fail-closed** until PP-001 wires Jakarta Mail / Quarkus Mailer; host=`none` also fails |
| `whatsapp.provider=logging` | `LoggingWhatsAppSender` | Demo only |
| `whatsapp.provider=meta` | `FailClosedWhatsAppSender` | **Fail-closed** until PP-002 `MetaWhatsAppSender` |

Do not set `smtp` / `meta` in customer-facing environments until the corresponding PP stories land and staging probes pass.

### Ops docs

| Doc | Content |
|-----|---------|
| `docs/deploy/EMAIL_DELIVERABILITY.md` | SPF/DKIM/DMARC, SES-as-SMTP or generic SMTP, env matrix, logging vs smtp testing, fail-closed |
| `docs/deploy/WHATSAPP_META_SETUP.md` | Meta Business, template names (`invoice_issued_en`, …), token/phone-number-id, webhook secret placeholders |
| `docs/deploy/PRODUCTION_PATH_RUNBOOK.md` | Staging secrets checklist, smoke (health → login → issue → notify), backup/restore pointers to AWS docs |

### Env keys (see `.env.example`)

- Notifications master + channel: `INVOICEGENIE_NOTIFICATIONS_*`
- SMTP: `INVOICEGENIE_SMTP_HOST|PORT|USERNAME|PASSWORD|FROM|STARTTLS`
- Meta: `INVOICEGENIE_WHATSAPP_ACCESS_TOKEN|PHONE_NUMBER_ID|API_VERSION` (+ planned webhook verify/app secret)
- Public links / callbacks: `INVOICEGENIE_PUBLIC_BASE_URL`
- OIDC placeholders: `INVOICEGENIE_OIDC_*` / `QUARKUS_OIDC_*` / `NEXT_PUBLIC_OIDC_*` (PP-020)

### Staging recommendation

1. Soak with **logging** email, WhatsApp **off**, `LOG_PAYLOADS=false`.
2. Validate SPF/DKIM and Meta templates in vendor consoles **before** flipping providers.
3. After PP-001/PP-002: promote provider flags only in staging first; watch attempt table + provider dashboards.