# InvoiceGenie — Demo Guide

**Audience:** Product Owner, stakeholders, pilot operators  
**Time:** ~20–25 minutes  
**Environment:** Local (`Development` branch)  
**Last validated:** 2026-07-27

---

## 0. Start the stack

```powershell
# 1) Database
docker compose up -d postgres adminer

# 2) API (from repo root) — host Postgres password must match .env
$env:QUARKUS_DATASOURCE_JDBC_URL = "jdbc:postgresql://localhost:5432/invoicegenie"
$env:QUARKUS_DATASOURCE_USERNAME = "ar"
$env:QUARKUS_DATASOURCE_PASSWORD = "change-me-strong-password"  # match .env
mvn -pl ar-bootstrap -am quarkus:dev "-Dquarkus.profile=dev" "-Dquarkus.http.port=8082"

# 3) Console (new terminal)
cd web
$env:BACKEND_URL = "http://localhost:8082"
npm run dev
```

| URL | Purpose |
|-----|---------|
| http://localhost:3000 | Operator console |
| http://localhost:8082/q/health | API health |
| http://localhost:8082/q/swagger-ui/ | OpenAPI |
| http://localhost:8081 | Adminer (SQL) |

**Login:** `admin@invoicegenie.local` / `Admin123!`  
**Tenant (demo):** `00000000-0000-0000-0000-000000000001`

> Notifications default to **logging** provider (safe demo). Messages appear in API logs as `[EMAIL-LOG]`, not real email.

---

## 1. Demo script (happy path)

### Act A — Security & multi-tenant (2 min)

1. Open http://localhost:3000 → login as admin.  
2. Show role-aware nav (Users, Tenants, Webhooks, Settings).  
3. **Talking point:** JWT session via BFF cookies; clerks cannot admin tenants.

### Act B — Customer & credit control (3 min)

1. **Customers** → create customer with email (required for notifications).  
2. Set a credit limit; open **AR summary**.  
3. Optionally block a test customer and show invoice issue rejects with clear error.

### Act C — Invoice lifecycle (4 min)

1. **Invoices** → create draft with qty/tax/discount lines.  
2. Edit draft if needed → **Issue**.  
3. Open invoice detail → **Download PDF**.  
4. **Talking point:** version snapshots + ledger Dr AR / Cr Revenue on issue.

### Act D — Collections messaging (4 min)

1. After issue, open **Notifications** — expect `INVOICE_ISSUED` progressing to SENT (logging).  
2. Show metrics tiles (SENT / FAILED / PENDING / SKIPPED).  
3. **Template preview** on Notifications page (event type + sample vars).  
4. Invoice → **Send notification** (idempotent if already sent).  
5. **Settings** (admin) → notification policy: quiet hours, attach PDF, channels.

### Act E — Cash application (4 min)

1. **Payments** → record payment for customer.  
2. Allocate FIFO or manual to the open invoice.  
3. Show invoice → PARTIALLY_PAID / PAID.  
4. Optional: reverse or unallocate (controller role).

### Act F — Cheques & credit notes (optional 3 min)

1. **Cheques** → create → deposit → clear (creates payment + allocation).  
2. **Credit notes** → apply and show AR impact / available credits.

### Act G — Aging, statements, webhooks (3 min)

1. **Aging** page + dashboard buckets.  
2. Customer → **Download statement PDF** / **Send statement**.  
3. **Webhooks** → subscription list; deliveries + redrive if any failed.  
4. **Audit** → export CSV; show actor on recent mutations.

### Act H — Close (1 min)

1. Summarize: bill → notify → collect → books balance → audit.  
2. Point to `docs/PO_NEXT_REVIEW.md` for next features with Product Owner.

---

## 2. API smoke (optional, 2 min)

With security hybrid/dev API key:

```http
GET  http://localhost:8082/q/health
POST http://localhost:8082/api/v1/auth/login
     { "email": "admin@invoicegenie.local", "password": "Admin123!" }
GET  http://localhost:8082/api/v1/notifications/metrics?days=7
     Authorization: Bearer <token>
     X-Tenant-Id: 00000000-0000-0000-0000-000000000001
```

Or run: `node scripts/smoke-ar.mjs` / `./scripts/test-api.ps1` if configured for port 8082.

---

## 3. What not to claim in the demo

| Topic | Reality |
|-------|---------|
| Customer received email | Logging provider only unless SMTP configured |
| WhatsApp on phone | Requires Meta token + approved templates |
| Production cloud | Staging deploy still ops-owned |
| Full ERP / AP / GL close | AR product only |

---

## 4. Troubleshooting

| Symptom | Fix |
|---------|-----|
| API cannot connect to DB | `docker compose up -d postgres`; JDBC URL `localhost:5432`; password matches compose |
| Login loop | Clear cookies; ensure `BACKEND_URL=http://localhost:8082` for web |
| No notifications | Customer must have email; policy enabled; check API logs for `[EMAIL-LOG]` |
| Port 8080 busy | Use `-Dquarkus.http.port=8082` and matching `BACKEND_URL` |

---

## 5. Success criteria for this demo

- [ ] Login works  
- [ ] Customer + issued invoice  
- [ ] Notification SENT (logging) visible in UI  
- [ ] Payment allocated; invoice paid/partial  
- [ ] PDF download works  
- [ ] Aging / metrics page loads without error  
