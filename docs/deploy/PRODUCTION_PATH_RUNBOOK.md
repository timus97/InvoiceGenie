# Production Path Runbook — Staging checklist & smoke (PP-042 / PP-043)

**Product:** InvoiceGenie AR  
**Audience:** Platform / DevOps / on-call  
**Date:** 2026-07-27  
**Scope:** Staging readiness and production-path smoke **without** requiring a live AWS deploy in this pass.  
**Source stories:** `docs/PRODUCTION_PATH_STORIES.md` (PP-040…PP-043)

---

## 1. Document map

| Doc | Use when |
|-----|----------|
| [EMAIL_DELIVERABILITY.md](./EMAIL_DELIVERABILITY.md) | SPF/DKIM, SES/SMTP, logging vs smtp |
| [WHATSAPP_META_SETUP.md](./WHATSAPP_META_SETUP.md) | Meta Business, templates, webhook secret |
| [PROD_EDGE_TLS.md](./PROD_EDGE_TLS.md) | nginx edge TLS, compose prod overlay, fail-closed security |
| [../aws/AWS_DEPLOYMENT_CHECKLIST.md](../aws/AWS_DEPLOYMENT_CHECKLIST.md) | Full AWS first-deploy checklist |
| [../aws/AWS_HOSTING_DESIGN.md](../aws/AWS_HOSTING_DESIGN.md) | Architecture, runbooks outline §12, backup/restore |
| [../aws/terraform/README.md](../aws/terraform/README.md) | IaC modules (staging/prod envs) |
| [../notifications/IMPLEMENTATION_NOTES.md](../notifications/IMPLEMENTATION_NOTES.md) | Notification code paths |
| [../PROJECT_STATUS.md](../PROJECT_STATUS.md) | Current product status |
| `.env.example` | Canonical env key list |

---

## 2. Staging checklist (pre-smoke)

### 2.1 Environment identity

- [ ] Environment name fixed: `staging` (never share secrets with prod)
- [ ] Public hostnames planned: web + API (or single edge host)
- [ ] `INVOICEGENIE_PUBLIC_BASE_URL` set to staging HTTPS API/edge base (for future unsubscribe/webhooks)
- [ ] Region decided (if AWS): matches SES region if using SES SMTP

### 2.2 Secrets inventory

Create **new** values — do **not** reuse `dev-local-key`, `Admin123!`, `ar`/`ar`, or `.env.example` placeholders in any shared staging that holds real customer data.

| Secret / config | Env key(s) | Storage |
|-----------------|------------|---------|
| DB password | `POSTGRES_PASSWORD`, `QUARKUS_DATASOURCE_PASSWORD` | Secrets Manager / `.env` |
| JDBC URL | `QUARKUS_DATASOURCE_JDBC_URL` | SSM or secret |
| API keys map | `INVOICEGENIE_API_KEYS` | Secret (`key:tenantUuid,...`) |
| JWT HS256 secret | `INVOICEGENIE_JWT_SECRET` (≥16, prefer ≥32 bytes) | Secret |
| Bootstrap admin (first boot only) | `INVOICEGENIE_BOOTSTRAP_EMAIL`, `INVOICEGENIE_BOOTSTRAP_PASSWORD` | Secret; change after first login |
| SMTP | `INVOICEGENIE_SMTP_*` | Secret |
| WhatsApp | `INVOICEGENIE_WHATSAPP_*` | Secret |
| Webhook verify / app secret (planned) | `INVOICEGENIE_WHATSAPP_WEBHOOK_VERIFY_TOKEN`, `INVOICEGENIE_WHATSAPP_APP_SECRET` | Secret |
| OIDC (optional path) | `INVOICEGENIE_OIDC_*` / Quarkus OIDC keys | Secret + IdP console |

AWS naming convention (illustrative): `invoicegenie/staging/db`, `invoicegenie/staging/api-keys`, `invoicegenie/staging/jwt` — see AWS checklist §3.

### 2.3 Security gates

- [ ] `QUARKUS_PROFILE=prod` (or profile string containing `prod`) for staging-as-prod
- [ ] `INVOICEGENIE_SECURITY_ENABLED=true`
- [ ] `INVOICEGENIE_SECURITY_MODE=hybrid` (or `api-key` / `jwt` per pilot decision)
- [ ] `INVOICEGENIE_SECURITY_ALLOW_OPENAPI=false`
- [ ] `NEXT_PUBLIC_ALLOW_TENANT_OVERRIDE=false`
- [ ] No demo API keys; `ProdSecurityValidator` would refuse known weak secrets
- [ ] Edge TLS terminated (ALB ACM or [PROD_EDGE_TLS.md](./PROD_EDGE_TLS.md) nginx)

### 2.4 Data plane

- [ ] Postgres 15+ reachable only from app network
- [ ] Flyway migrate-at-start true on first deploy; confirm history includes **V1–V12** (notifications)
- [ ] Backup retention enabled (RDS ≥ 7 days staging; see AWS checklist §4 / §13)
- [ ] SSL to DB (`sslmode=require` on RDS JDBC URL)

### 2.5 Notifications stance for this staging

| Mode | When to use |
|------|-------------|
| **Logging only** | Default for functional AR smoke; no external side effects |
| **SMTP fail-closed probe** | Verify config wiring without delivery |
| **Real SMTP/SES** | Only after PP-001 transport + SPF/DKIM ([EMAIL_DELIVERABILITY.md](./EMAIL_DELIVERABILITY.md)) |
| **WhatsApp meta** | Only after PP-002 + approved templates ([WHATSAPP_META_SETUP.md](./WHATSAPP_META_SETUP.md)) |

Recommended first staging soak:

```bash
INVOICEGENIE_NOTIFICATIONS_ENABLED=true
INVOICEGENIE_NOTIFICATIONS_EMAIL_ENABLED=true
INVOICEGENIE_NOTIFICATIONS_EMAIL_PROVIDER=logging
INVOICEGENIE_NOTIFICATIONS_WHATSAPP_ENABLED=false
INVOICEGENIE_NOTIFICATIONS_LOG_PAYLOADS=false
```

### 2.6 Local prod-like compose (optional)

```bash
cp .env.example .env
# set strong POSTGRES_PASSWORD, QUARKUS_DATASOURCE_PASSWORD, INVOICEGENIE_API_KEYS, JWT secret
mkdir -p certs
# generate short-lived self-signed certs for local only — see PROD_EDGE_TLS.md
docker compose -f docker-compose.prod.yml up -d --build
curl -k https://localhost/q/health
```

---

## 3. Smoke steps

Replace `BASE`, auth headers, and UUIDs for your environment. Default local API is often **8080** in containers / **8082** in some dev docs — confirm the running port.

### 3.1 Health

```bash
export BASE=https://staging.example.com   # or http://localhost:8080
curl -sS "$BASE/q/health"
# expect UP (or Quarkus health JSON with status UP)
```

Optional: `curl -sS "$BASE/q/health/ready"` if readiness split is enabled.

### 3.2 Login (web JWT)

1. Open web console (`https://…` or `http://localhost:3000`).
2. Login with bootstrap or provisioned user (staging: **not** default password after first use).
3. Confirm session cookie / JWT path works; Settings shows session; **tenant override hidden**.
4. API-only alternative:

```bash
curl -sS -X POST "$BASE/api/v1/auth/login" \
  -H "Content-Type: application/json" \
  -d '{"email":"admin@invoicegenie.local","password":"<staging-password>"}'
# capture access token
export TOKEN=...
```

Or M2M:

```bash
export KEY=<staging-api-key>
export TENANT=00000000-0000-0000-0000-000000000001
```

### 3.3 Issue invoice (core AR)

```bash
# Customer
curl -sS -X POST "$BASE/api/v1/customers" \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer $TOKEN" \
  -H "X-Tenant-Id: $TENANT" \
  -H "X-API-Key: $KEY" \
  -d '{"customerCode":"STG1","legalName":"Staging Smoke Co","currency":"USD","email":"you@example.com"}'

# Invoice (add Idempotency-Key on create)
curl -sS -X POST "$BASE/api/v1/invoices" \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer $TOKEN" \
  -H "X-Tenant-Id: $TENANT" \
  -H "X-API-Key: $KEY" \
  -H "Idempotency-Key: $(uuidgen || echo smoke-inv-1)" \
  -d '{"invoiceNumber":"STG-INV-1","customerId":"<customer-uuid>","currencyCode":"USD","dueDate":"2026-08-15","lines":[{"sequence":1,"description":"Smoke","amount":100}]}'

# Issue (path may be POST .../invoices/{id}/issue — use UI or OpenAPI in non-prod)
```

UI path: Customers → create → Invoices → create → **Issue**.

Automated partial smoke: `node scripts/smoke-ar.mjs` with `API_BASE` and security headers as your env requires (script defaults assume open local API — extend headers when security is on).

### 3.4 Notify (customer notifications)

**Logging mode (expected green):**

1. After issue, wait for outbox worker + notification dispatch (dispatch interval default **15s**).
2. UI: **Notifications** → row for `INVOICE_ISSUED` / EMAIL → **SENT**.
3. Logs: `[EMAIL-LOG]` with redacted destination when `LOG_PAYLOADS=false`.
4. Manual re-send (idempotent without force for same key):

```http
POST /api/v1/notifications/send
{
  "invoiceId": "<uuid>",
  "eventType": "INVOICE_ISSUED",
  "channels": ["EMAIL"],
  "force": true
}
```

**SMTP mode (post PP-001):** follow EMAIL_DELIVERABILITY testing matrix; confirm inbox + attempt success.

**WhatsApp:** keep off until Meta templates approved; then WHATSAPP_META_SETUP §7.

### 3.5 Smoke exit criteria

| Check | Pass |
|-------|------|
| Health UP | Yes |
| Unauthenticated API call | 401/403 when security on |
| Login / API key | Success |
| Issue invoice | Status ISSUED (or equivalent) |
| Notification (logging) | SENT attempt recorded |
| OpenAPI | Not public on prod-like edge |
| Cross-tenant peek | Empty/404 (spot check) |

---

## 4. Backup / restore notes

InvoiceGenie does **not** invent a separate backup tool. Use the platform data plane:

### 4.1 AWS RDS (preferred for staging/prod)

Follow:

- [AWS_DEPLOYMENT_CHECKLIST.md §4 Data plane](../aws/AWS_DEPLOYMENT_CHECKLIST.md) — Multi-AZ, retention, deletion protection  
- [§13 Backup / restore drill](../aws/AWS_DEPLOYMENT_CHECKLIST.md) — manual snapshot → restore → temporary API task → validate  
- [AWS_HOSTING_DESIGN.md §9 / §12](../aws/AWS_HOSTING_DESIGN.md) — migration cutover and runbook outline  

Minimum drill before go-live:

1. Take **manual snapshot**.  
2. Restore to a **new** instance in the same VPC (or staging VPC).  
3. Point a one-off API task at restored JDBC URL (`sslmode=require`).  
4. Validate: `flyway_schema_history`, row counts for a known tenant, login, one invoice read.  
5. Record **RTO** (time to serve traffic from restore) and **RPO** (snapshot age).  

### 4.2 Local / compose Postgres

```bash
# backup
docker compose exec -T postgres pg_dump -U ar invoicegenie > backup-$(date +%Y%m%d).sql

# restore (destructive to target DB — use empty volume or drop/recreate)
docker compose exec -T postgres psql -U ar -d invoicegenie < backup-YYYYMMDD.sql
```

### 4.3 App-level notes

- Flyway owns schema — restore DB **with** `flyway_schema_history` intact; do not “fix” by wiping history on a non-empty schema.
- Secrets are **not** in the DB dump; re-bind Secrets Manager / `.env` after restore.
- Object storage (if added later for PDFs) needs a separate backup plan; MVP PDFs may be generated on the fly (PP-012).

---

## 5. Rollback (app)

1. Redeploy previous ECS task definition / previous compose image tags (`sha-…`).  
2. Do **not** blindly re-run destructive SQL.  
3. If a Flyway migration is bad: restore DB from snapshot taken **before** migrate, or forward-fix with a new migration — never edit applied scripts.  
4. Keep DNS TTL low during cutover windows (AWS design §9.3).

---

## 6. OIDC optional path (PP-020) — ops placeholders

When `INVOICEGENIE_SECURITY_MODE=oidc` (or hybrid with IdP JWT) is implemented:

| Concern | Notes |
|---------|-------|
| Issuer / JWKS | Configure Quarkus OIDC or JWT issuer validation |
| Web login | Document redirect URIs on IdP (Cognito/Keycloak/Auth0) |
| Env | See `.env.example` OIDC section |
| Fallback | Keep `hybrid` API-key + app JWT for M2M and bootstrap |

Until code lands, staging continues with **hybrid** email/password JWT + API keys.

---

## 7. Sign-off (staging soak)

| Role | Name | Date | Notes |
|------|------|------|-------|
| Engineering | | | Smoke §3 green |
| Ops | | | Secrets + backup drill |
| Product / Security | | | Auth mode accepted |

Promote to production only after staging soak (AWS checklist suggests ≥ 48h) and notification provider choice is explicit (logging vs real).
