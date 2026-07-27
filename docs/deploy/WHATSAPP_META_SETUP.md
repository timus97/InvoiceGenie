# WhatsApp Meta Cloud API Setup (PP-041)

**Product:** InvoiceGenie AR  
**Audience:** Platform / DevOps + whoever owns Meta Business  
**Status:** Ops-ready documentation (no live Meta traffic required to read this)  
**Code status (as of 2026-07-27):**  
- `whatsapp.provider=logging` — **implemented** (demo)  
- `whatsapp.provider=meta` — **fail-closed** via `FailClosedWhatsAppSender` until PP-002 (`MetaWhatsAppSender`) lands  
- Template **names** seeded in Flyway V12 for Meta mapping

---

## 1. Goals

1. Create / configure Meta Business + WhatsApp Cloud API assets.
2. Approve **message templates** that match InvoiceGenie seed names.
3. Configure env vars (token, phone-number-id, API version, provider flags).
4. Plan **webhook** verification secret for delivery/status callbacks (PP-003).
5. Fail closed safely when Meta is selected without a working client or credentials.

---

## 2. Architecture (target)

```
InvoiceGenie dispatch worker
    → WhatsAppSender (provider=meta)
    → POST https://graph.facebook.com/{api-version}/{phone-number-id}/messages
         Authorization: Bearer <access-token>
         body: template message (name + language + components)
```

Config keys (`application.yml`):

| YAML | Env |
|------|-----|
| `invoicegenie.notifications.whatsapp.enabled` | `INVOICEGENIE_NOTIFICATIONS_WHATSAPP_ENABLED` |
| `invoicegenie.notifications.whatsapp.provider` | `INVOICEGENIE_NOTIFICATIONS_WHATSAPP_PROVIDER` |
| `invoicegenie.notifications.whatsapp-meta.access-token` | `INVOICEGENIE_WHATSAPP_ACCESS_TOKEN` |
| `invoicegenie.notifications.whatsapp-meta.phone-number-id` | `INVOICEGENIE_WHATSAPP_PHONE_NUMBER_ID` |
| `invoicegenie.notifications.whatsapp-meta.api-version` | `INVOICEGENIE_WHATSAPP_API_VERSION` |

Defaults: WhatsApp **disabled**, provider **logging**, token/phone id **`none`**, API version **`v18.0`**.

---

## 3. Meta Business setup (console)

Perform in [Meta Business Suite](https://business.facebook.com/) / developers.facebook.com:

1. **Business Manager** account for the org (or sandbox Business for staging).
2. Create a **Meta App** (type that supports WhatsApp) and add the **WhatsApp** product.
3. Attach a **WhatsApp Business Account (WABA)**.
4. Add / claim a **business phone number** (or use Meta’s test number in dev).
5. Note:
   - **Phone number ID** (Graph API id — **not** the E.164 display number alone)
   - **WABA ID** (for template admin / webhooks)
6. Generate a **System User** access token (or long-lived token) with WhatsApp send permissions:
   - Typical permissions: `whatsapp_business_messaging`, `whatsapp_business_management`
7. Store the token in Secrets Manager / `.env` as `INVOICEGENIE_WHATSAPP_ACCESS_TOKEN`.
8. Never commit tokens; rotate on staff change.

### 3.1 Staging vs production

| Env | Recommendation |
|-----|----------------|
| Local | `provider=logging`, WhatsApp **disabled** |
| Staging | Dedicated test number + test templates; low rate |
| Production | Production phone number; approved templates; secrets only via SM |

---

## 4. Message templates

Meta only allows **pre-approved template** messages for business-initiated conversations (invoice issue, reminders, dunning). InvoiceGenie maps DB field `whatsapp_template_name` to Meta’s template name.

### 4.1 Seeded names (Flyway V12)

| Event type | Seeded `whatsapp_template_name` | Body variables (body text in DB) |
|------------|----------------------------------|----------------------------------|
| `INVOICE_ISSUED` | `invoice_issued_en` | invoiceNumber, balanceDue, currency, dueDate, tenantName |
| `PAYMENT_REMINDER` | `payment_reminder_en` | invoiceNumber, balanceDue, currency, dueDate |
| `DUNNING_NOTICE` | `dunning_notice_en` | invoiceNumber, balanceDue, currency, dueDate |

Create matching templates in Meta (language **en** or `en_US` per Meta UI) whose **names** equal the seed values (or update the DB template rows to match Meta’s approved names).

### 4.2 Template design tips

- Category: **UTILITY** (transactional invoice/reminder) is usually correct; marketing category has stricter opt-in rules.
- Keep variable order stable — Meta components are positional; align with renderer `{{var}}` order used by the Meta client (PP-002).
- Do not put PII in the template **name**.
- Submit for approval early; approval can take hours to days.

### 4.3 Customer destination format

Store customer phone as **E.164** (e.g. `+15551234567`). Logging provider accepts demo values; Meta requires a valid WhatsApp-capable number.

---

## 5. Environment variables

### 5.1 Demo / local (safe defaults)

```bash
INVOICEGENIE_NOTIFICATIONS_WHATSAPP_ENABLED=false
INVOICEGENIE_NOTIFICATIONS_WHATSAPP_PROVIDER=logging
INVOICEGENIE_WHATSAPP_ACCESS_TOKEN=none
INVOICEGENIE_WHATSAPP_PHONE_NUMBER_ID=none
INVOICEGENIE_WHATSAPP_API_VERSION=v18.0
```

### 5.2 Staging / production (after PP-002)

```bash
INVOICEGENIE_NOTIFICATIONS_ENABLED=true
INVOICEGENIE_NOTIFICATIONS_WHATSAPP_ENABLED=true
INVOICEGENIE_NOTIFICATIONS_WHATSAPP_PROVIDER=meta
INVOICEGENIE_WHATSAPP_ACCESS_TOKEN=<system-user-or-permanent-token>
INVOICEGENIE_WHATSAPP_PHONE_NUMBER_ID=<phone-number-id>
INVOICEGENIE_WHATSAPP_API_VERSION=v18.0
INVOICEGENIE_NOTIFICATIONS_LOG_PAYLOADS=false
```

Also ensure tenant **notification policy** has WhatsApp enabled if the product gates on policy + prefs (customer opt-in).

### 5.3 Webhook secret (PP-003 / provider webhooks)

When delivery status / bounce-style callbacks are wired:

| Env (planned) | Purpose |
|---------------|---------|
| `INVOICEGENIE_WHATSAPP_WEBHOOK_VERIFY_TOKEN` | Meta “Verify Token” for `GET` subscription challenge |
| `INVOICEGENIE_WHATSAPP_APP_SECRET` | HMAC validation of `X-Hub-Signature-256` on `POST` webhooks |
| `INVOICEGENIE_PUBLIC_BASE_URL` | Public HTTPS base for callback URL, e.g. `https://api.yourdomain.com` |

Planned callback path (PP-003):

```http
POST /api/v1/notifications/provider-webhooks/meta
```

In Meta App → WhatsApp → Configuration:

- Callback URL: `{PUBLIC_BASE_URL}/api/v1/notifications/provider-webhooks/meta`
- Verify token: same value as `INVOICEGENIE_WHATSAPP_WEBHOOK_VERIFY_TOKEN`
- Subscribe to message status fields as required (`messages`, delivery/read if available)

Until PP-003 is implemented, configure webhooks only if you need them for external tooling; the app will not process them yet.

---

## 6. Fail-closed behavior

| Condition | Result |
|-----------|--------|
| `whatsapp.provider=meta` today (pre PP-002) | `FailClosedWhatsAppSender` returns failure — **no** Graph call, **no** false SENT |
| Channel `WHATSAPP_ENABLED=false` | Dispatcher fails attempt with “Channel disabled…” |
| Missing token / phone-number-id (post PP-002 target) | Client must fail closed (same pattern as SMTP host=`none`) |
| Customer opted out / no phone | Enqueue **SKIPPED** (preference / validation path) |

**Never** set `provider=meta` in production until PP-002 is deployed and staging Graph calls succeed.

---

## 7. Testing

### 7.1 Logging provider

```bash
INVOICEGENIE_NOTIFICATIONS_WHATSAPP_ENABLED=true
INVOICEGENIE_NOTIFICATIONS_WHATSAPP_PROVIDER=logging
```

1. Customer with phone + WhatsApp pref on.
2. Manual send or issue path with WhatsApp channel.
3. Expect `[WHATSAPP-LOG]` and status **SENT**.

### 7.2 Meta fail-closed (current)

```bash
INVOICEGENIE_NOTIFICATIONS_WHATSAPP_ENABLED=true
INVOICEGENIE_NOTIFICATIONS_WHATSAPP_PROVIDER=meta
```

Expect: attempt errors containing “not implemented” / fail-closed; status RETRY → FAILED.

### 7.3 Meta live (after PP-002)

1. Use Meta test number + your WhatsApp personal number as customer.
2. Templates must be **Approved**.
3. Send `INVOICE_ISSUED` once; confirm WhatsApp receipt and attempt `providerMessageId` (wamid).
4. Revoke token → expect failures without crash loops (backoff).
5. Confirm logs do not print full bodies when `LOG_PAYLOADS=false`.

### 7.4 Graph API manual probe (ops)

```bash
curl -s -X POST "https://graph.facebook.com/v18.0/${PHONE_NUMBER_ID}/messages" \
  -H "Authorization: Bearer ${TOKEN}" \
  -H "Content-Type: application/json" \
  -d "{\"messaging_product\":\"whatsapp\",\"to\":\"15551234567\",\"type\":\"template\",\"template\":{\"name\":\"invoice_issued_en\",\"language\":{\"code\":\"en\"}}}"
```

Use this to validate Meta credentials **before** pointing the app at `provider=meta`.

---

## 8. Security & compliance

- Tokens are **secrets** — Secrets Manager / sealed `.env` only.
- Prefer **system user** tokens with least privilege over personal user tokens.
- Respect customer **opt-out** (preferences API/UI); Meta may also require STOP handling for some categories.
- Rate limits: app-level `send-rate-per-minute` + Meta tier limits — monitor 429s after go-live.
- Webhook secrets must differ per environment (staging ≠ prod).
- Do not log access tokens or full webhook payloads with PII in shared log drains.

---

## 9. Related docs

- `docs/notifications/IMPLEMENTATION_NOTES.md`  
- `docs/deploy/EMAIL_DELIVERABILITY.md`  
- `docs/deploy/PRODUCTION_PATH_RUNBOOK.md`  
- `docs/PROJECT_STATUS.md`, `docs/DEMO.md`
