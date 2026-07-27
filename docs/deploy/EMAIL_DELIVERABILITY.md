# Email Deliverability — SPF/DKIM, SES or SMTP (PP-040)

**Product:** InvoiceGenie AR  
**Audience:** Platform / DevOps  
**Status:** Ops-ready documentation (no live AWS deploy required)  
**Code status (as of 2026-07-27):**  
- `email.provider=logging` — **implemented** (demo; logs only)  
- `email.provider=smtp` — **fail-closed** until real Jakarta Mail / Quarkus Mailer transport lands (PP-001)  
- SES can be used as **SMTP relay** once SMTP transport is real, or as a future native adapter

Related code:

| Piece | Location |
|-------|----------|
| Config | `ar-bootstrap/src/main/resources/application.yml` → `invoicegenie.notifications.*` |
| Dispatch | `NotificationDispatchWorker` |
| Logging provider | `LoggingEmailSender` |
| SMTP provider | `SmtpEmailSender` (fail-closed when host=`none` or transport missing) |
| Env template | `.env.example` |

---

## 1. Goals

1. Choose a production email path: **Amazon SES** (recommended on AWS) or **generic SMTP**.
2. Authenticate mail for the sending domain (**SPF**, **DKIM**, optional **DMARC**).
3. Configure InvoiceGenie env so the dispatcher uses the intended provider.
4. Understand **fail-closed** behavior (no silent “SENT” without transport).
5. Validate with **logging** first, then SMTP/SES in staging.

---

## 2. Provider modes

| `INVOICEGENIE_NOTIFICATIONS_EMAIL_PROVIDER` | Behavior |
|---------------------------------------------|----------|
| `logging` (default) | Writes `[EMAIL-LOG]` lines; marks notification **SENT** with a synthetic provider id. **No external delivery.** Use for local demo / CI. |
| `smtp` | Resolves `SmtpEmailSender`. Requires `INVOICEGENIE_SMTP_HOST` ≠ `none`. Until PP-001, sender **fails closed** (marks retry/FAILED; never pretends success). |

Master switches:

| Env | Default | Role |
|-----|---------|------|
| `INVOICEGENIE_NOTIFICATIONS_ENABLED` | `true` | Master kill switch for enqueue + dispatch |
| `INVOICEGENIE_NOTIFICATIONS_EMAIL_ENABLED` | `true` | Channel gate in dispatcher |
| `INVOICEGENIE_NOTIFICATIONS_LOG_PAYLOADS` | `false` | When `false`, destinations/bodies redacted in logs (QA-NOTIFY-003) |

---

## 3. SMTP configuration (generic + SES-as-SMTP)

Map to `invoicegenie.notifications.smtp.*`:

| Env | Default | Notes |
|-----|---------|-------|
| `INVOICEGENIE_SMTP_HOST` | `none` | SMTP hostname. **`none` or blank → fail closed** |
| `INVOICEGENIE_SMTP_PORT` | `587` | Use `587` + STARTTLS (preferred) or `465` (SMTPS; may need app support) |
| `INVOICEGENIE_SMTP_USERNAME` | `none` | SMTP auth user (SES SMTP credentials user) |
| `INVOICEGENIE_SMTP_PASSWORD` | `none` | SMTP auth password — store in Secrets Manager / `.env` only |
| `INVOICEGENIE_SMTP_FROM` | `noreply@invoicegenie.local` | Must be a verified identity/domain in SES or allowed by your relay |
| `INVOICEGENIE_SMTP_STARTTLS` | `true` | Keep `true` for port 587 |

### 3.1 Amazon SES as SMTP relay (recommended on AWS)

1. In **SES** (same region as app when possible):
   - Verify **domain** (or at least the From address) for the env.
   - Move out of **sandbox** before sending to arbitrary recipients (request production access).
   - Create **SMTP credentials** (IAM user restricted to SES send).
2. SES SMTP endpoint form: `email-smtp.<region>.amazonaws.com` (port **587**).
3. Set env (example staging):

```bash
INVOICEGENIE_NOTIFICATIONS_ENABLED=true
INVOICEGENIE_NOTIFICATIONS_EMAIL_ENABLED=true
INVOICEGENIE_NOTIFICATIONS_EMAIL_PROVIDER=smtp
INVOICEGENIE_SMTP_HOST=email-smtp.us-east-1.amazonaws.com
INVOICEGENIE_SMTP_PORT=587
INVOICEGENIE_SMTP_USERNAME=<ses-smtp-username>
INVOICEGENIE_SMTP_PASSWORD=<ses-smtp-password>
INVOICEGENIE_SMTP_FROM=noreply@mail.yourdomain.com
INVOICEGENIE_SMTP_STARTTLS=true
INVOICEGENIE_NOTIFICATIONS_LOG_PAYLOADS=false
```

4. Store secrets in **AWS Secrets Manager** (see `docs/aws/AWS_DEPLOYMENT_CHECKLIST.md` §3) and inject into ECS task definitions — never bake into images.

### 3.2 Third-party SMTP (SendGrid, Mailgun, corporate relay)

Same keys; use the provider’s SMTP host/port and From domain they authorized. Prefer STARTTLS on 587.

---

## 4. SPF / DKIM / DMARC (deliverability)

Do this on the **sending domain** (the domain of `INVOICEGENIE_SMTP_FROM`), not on the app hostname alone.

### 4.1 SPF

Publish a TXT record on the envelope domain, e.g.:

```text
; SES
v=spf1 include:amazonses.com ~all

; Or your SMTP vendor’s include:
; v=spf1 include:sendgrid.net ~all
```

- One SPF TXT per domain (merge includes; avoid multiple SPF records).
- Prefer `~all` during rollout; tighten to `-all` after validation.

### 4.2 DKIM

- **SES:** Enable Easy DKIM on the verified domain; publish the CNAME records SES provides.
- **Other SMTP:** Enable domain authentication in the vendor console and publish their DKIM CNAMEs/TXTs.
- Align **From** domain with the DKIM-signing domain (or configure aligned friendly From carefully).

### 4.3 DMARC (recommended)

```text
_dmarc.yourdomain.com TXT
v=DMARC1; p=none; rua=mailto:dmarc-reports@yourdomain.com; fo=1
```

Start with `p=none`, monitor, then `quarantine` / `reject`.

### 4.4 Checklist before customer traffic

- [ ] Domain (or mailbox) verified in SES / SMTP vendor  
- [ ] SPF includes the sending service  
- [ ] DKIM signing enabled and DNS green  
- [ ] DMARC at least `p=none` with reporting  
- [ ] From address matches verified identity  
- [ ] SES production access (out of sandbox) if sending externally  
- [ ] Bounce/complaint path planned (PP-003 provider webhooks — future)

---

## 5. Fail-closed behavior (critical)

InvoiceGenie intentionally **does not** mark email **SENT** when SMTP is misconfigured or transport is unimplemented:

| Condition | Result |
|-----------|--------|
| `email.provider=smtp` and `SMTP_HOST` is `none`/blank | `SmtpEmailSender` returns failure → worker schedules **RETRY** then **FAILED** after max attempts |
| `email.provider=smtp` and transport not implemented (pre PP-001) | Same — fail closed with clear error in attempt log |
| `email.provider=smtp` but bean missing | Worker fails with “SmtpEmailSender bean unavailable” |
| Channel disabled (`EMAIL_ENABLED=false`) | Notification marked **FAILED** with “Channel disabled in runtime config” |
| Master `NOTIFICATIONS_ENABLED=false` | Dispatch worker no-ops |

**Demo path:** keep `EMAIL_PROVIDER=logging` so smoke tests show SENT without external mail.

**Production path:** set `smtp` only after PP-001 transport is deployed **and** SPF/DKIM verified in staging.

Retries: exponential backoff (`NotificationDispatchWorker`), max attempts from `INVOICEGENIE_NOTIFICATIONS_MAX_ATTEMPTS` (default 5).

---

## 6. Testing matrix: logging vs SMTP

### 6.1 Logging provider (local / CI)

```bash
# Defaults are fine; explicit:
INVOICEGENIE_NOTIFICATIONS_EMAIL_PROVIDER=logging
INVOICEGENIE_NOTIFICATIONS_EMAIL_ENABLED=true
INVOICEGENIE_NOTIFICATIONS_LOG_PAYLOADS=false   # redacted
# optional full payload for local only:
# INVOICEGENIE_NOTIFICATIONS_LOG_PAYLOADS=true
```

Steps:

1. Start Postgres + API (`mvn -pl ar-bootstrap -am quarkus:dev -Dquarkus.profile=dev`) + web.
2. Login (`admin@invoicegenie.local` / `Admin123!`).
3. Ensure customer has an **email**.
4. Create + **Issue** invoice → wait for outbox + dispatch (~15s interval).
5. Confirm API history: **Notifications** UI or `GET /api/v1/notifications` → status **SENT**.
6. Confirm logs: `[EMAIL-LOG]` (payload redacted unless log-payloads=true).

### 6.2 SMTP fail-closed smoke (pre / post PP-001)

```bash
INVOICEGENIE_NOTIFICATIONS_EMAIL_PROVIDER=smtp
INVOICEGENIE_SMTP_HOST=none
```

Expect: attempts fail with “SMTP host not configured…”, notification eventually **FAILED** (or RETRY while attempts remain).

With host set but transport not yet implemented: log line `[EMAIL-SMTP] transport not implemented — failing closed`.

### 6.3 Staging SMTP/SES (after PP-001)

1. Use a **dedicated staging From** and a mailbox you control as customer email.
2. Issue invoice or `POST /api/v1/notifications/send` with `force` per product rules.
3. Assert:
   - Attempt row shows provider name `smtp` and success.
   - Message appears in inbox (check spam).
   - Headers show DKIM pass / SPF pass (Gmail “Show original”, mail-tester.com).
4. Negative tests:
   - Wrong password → retries then FAILED  
   - Unverified From (SES) → provider reject → FAILED  
5. Confirm `log-payloads=false` in shared staging logs (no raw customer email bodies).

### 6.4 Manual API example

```http
POST /api/v1/notifications/send
Authorization: Bearer <jwt>
Content-Type: application/json

{
  "invoiceId": "<uuid>",
  "eventType": "INVOICE_ISSUED",
  "channels": ["EMAIL"],
  "force": true
}
```

---

## 7. Ops notes

- **Rate limit:** `INVOICEGENIE_NOTIFICATIONS_SEND_RATE_PER_MINUTE` (default 30) applies to manual send path.
- **PII:** never enable `LOG_PAYLOADS` in production.
- **At-least-once:** provider side effects are not in the same DB transaction as status; design assumes possible retry — prefer provider idempotency when available (see notifications QA notes).
- **SES reputation:** warm up volume; monitor bounces (wire PP-003 suppressions when available).
- **Public links** (unsubscribe, PDF — future): set `INVOICEGENIE_PUBLIC_BASE_URL` / web public URL so email footers point at the edge HTTPS host, not `localhost`.

---

## 8. Related docs

- `docs/notifications/IMPLEMENTATION_NOTES.md`  
- `docs/notifications/ARCHITECTURE_NOTIFICATIONS.md`  
- `docs/deploy/PRODUCTION_PATH_RUNBOOK.md`  
- `docs/aws/AWS_DEPLOYMENT_CHECKLIST.md` (secrets, staging soak)  
- `docs/deploy/PROD_EDGE_TLS.md` (TLS edge for public links)
