# Production Path — Engineering Stories (Active Implementation)

**Program owner:** Engineering Team Lead  
**Date:** 2026-07-27  
**Source of truth:** `docs/PROJECT_REVIEW_STAKEHOLDER.md` §7–§12  
**Goal:** Close all implementable pending / partial / production-path items for next stakeholder review.

## Workstream assignment

| Stream | Role | Stories | Owner agent |
|--------|------|---------|-------------|
| WS-MSG | Backend Messaging | PP-001, PP-002, PP-003, PP-004 | Agent BE-Messaging |
| WS-COL | Backend Collections | PP-010…PP-016 | Agent BE-Collections |
| WS-SEC | Backend Security & Platform | PP-020…PP-026 | Agent BE-Security |
| WS-FE | Frontend | PP-030…PP-036 | Agent FE |
| WS-OPS | Platform / Docs | PP-040…PP-043 | Agent Ops |
| WS-QA | QA | PP-050…PP-052 | Agent QA (after merge) |

## Story catalog

### PP-001 Real SMTP email (Jakarta Mail / Quarkus Mailer)
- Implement real transport in `SmtpEmailSender` (or `QuarkusMailerEmailSender`).
- Prefer `quarkus-mailer` if clean with multi-module; else Jakarta Mail.
- Config: host/port/user/pass/from/starttls already under `invoicegenie.notifications.smtp.*`.
- Fail closed when host=none; success only after provider accept.
- Unit test with mock transport or pure validation path.
- Wire `NotificationDispatchWorker` for `provider=smtp`.

### PP-002 Meta WhatsApp Cloud API client
- New `MetaWhatsAppSender` implementing `WhatsAppSender`.
- POST `https://graph.facebook.com/{version}/{phone-number-id}/messages` with template payload.
- Map notification template name + body variables.
- Fail closed without token/phone-number-id.
- Wire dispatcher when `whatsapp.provider=meta`.

### PP-003 Provider delivery / bounce webhooks
- `POST /api/v1/notifications/provider-webhooks/{provider}` (ses|smtp-generic|meta).
- Persist suppressions: hard bounce / complaint → block destination.
- Flyway V13: `ar_notification_suppression` (tenant_id, channel, destination_hash, reason, created_at).
- Enqueue skips suppressed destinations.

### PP-004 Channel fallback
- Policy flag `channelFallbackEnabled` (email after WhatsApp fail or reverse per config).
- On FAILED WhatsApp with fallback, enqueue EMAIL once (new idempotency key suffix).

### PP-010 Quiet hours
- Policy: `quietHoursStart`, `quietHoursEnd` (tenant local or UTC documented).
- Dispatch defers sends during quiet hours (reschedule nextAttempt).

### PP-011 Unsubscribe links
- Tokenized unsubscribe URL in email footer.
- `POST /api/v1/notifications/unsubscribe?token=` public (rate limited).
- Sets preference opt-out for channel.

### PP-012 PDF invoice + statement
- Generate PDF via PDFBox (already on classpath for OCR).
- `GET /api/v1/invoices/{id}/pdf`, `GET .../statement?format=pdf`.
- Optional attach on INVOICE_ISSUED when policy `attachPdfOnIssue=true`.

### PP-013 Statement send workflow
- `POST /api/v1/customers/{id}/statement/send` → enqueue notification STATEMENT_SEND or email with PDF.

### PP-014 Template preview API
- `POST /api/v1/notifications/templates/preview` with sample vars → rendered subject/body (no send).

### PP-015 Notification metrics
- `GET /api/v1/notifications/metrics` counts by status last 24h/7d for dashboard.

### PP-016 QA-NOTIFY-016 audit stream
- Audit entries on policy put, preference put, manual send.

### PP-020 OIDC optional path
- Config-driven: when `invoicegenie.security.mode=oidc`, document + implement Quarkus OIDC JWT validation path OR hybrid accept external issuer JWTs.
- Keep existing login for `mode=jwt|api-key|hybrid`.
- Web: document env for OIDC login redirect if feasible; minimum API bearer from IdP.

### PP-021 DEF-BE-006 config keys
- Fix `%dev` datasource username/password keys to recognized Quarkus keys.

### PP-022 DEF-FE-002 default port 8082
- Align web defaults, gen:api, env examples to 8082.

### PP-023 Cursor pagination
- Payments + notifications list: `cursor`/`nextCursor` (created_at,id).

### PP-024 Cross-currency allocation lite
- When currencies differ, convert via ExchangeRate as-of payment date; post FX gain/loss if amounts differ; or clear 400 with optional `allowFx=true`.

### PP-025 Period close MVP
- Tenant setting open/close period; block issue/pay outside open period (config or table).

### PP-026 Webhook redrive API
- `POST /api/v1/webhooks/deliveries/{id}/redrive` for DEAD deliveries.

### PP-030…036 Frontend
- Metrics widgets, template preview, quiet hours + attach PDF policy UI, unsubscribe is public page, PDF download buttons, webhook redrive button, OIDC/login copy, port 8082.

### PP-040…043 Ops
- Restore docs (done S0), email deliverability runbook, WhatsApp setup runbook, update PROJECT_STATUS + stakeholder residual table.

### PP-050…052 QA
- Unit tests for new services; expand Playwright smoke for notify metrics path if stable; multi-tenant isolation test if time.

## Out of scope for this implementation pass (track only)
- Live AWS deploy / real ALB certs (ops environment)
- Full AP module product
- Multi-region active-active
- GraalVM native image
- Customer payment portal + PSP (design only if time)

## Definition of Done (program)
- [ ] `mvn -B test` green
- [ ] `cd web && npm run lint` green  
- [ ] Real SMTP path unit-tested; Meta client unit-tested with mocked HTTP
- [ ] Flyway V13 applied in tests
- [ ] Stakeholder doc §7 residuals updated to Done/Partial new truth
- [ ] PROJECT_STATUS updated

