# AWS Deployment Checklist — InvoiceGenie AR

> First production (or staging-as-prod) deploy.  
> Pair with [AWS_HOSTING_DESIGN.md](./AWS_HOSTING_DESIGN.md).  
> Codebase references: `Dockerfile.prod`, `web/Dockerfile`, `docker-compose.yml`, `ar-bootstrap/.../application.yml`, `.env.example`.

Use `[ ]` → `[x]` as you go. Prefer completing **Staging** fully before **Production**.

---

## 0. Prerequisites (local / org)

- [ ] AWS account with billing alerts configured
- [ ] IAM Identity Center (or IAM admin) access for bootstrap
- [ ] Domain name available (or accept ALB DNS temporarily)
- [ ] GitHub repo access; Actions enabled
- [ ] JDK 17 + Maven 3.9+ + Docker for image builds
- [ ] Decision recorded: primary region (e.g. `us-east-1`)
- [ ] Secrets policy: no secrets in git; `.env` gitignored
- [ ] Quarkus EOL risk accepted or LTS migration scheduled (`docs/QUARKUS_LTS_MIGRATION.md`)
- [ ] Auth mode for pilot: `api-key` and/or `jwt` (Cognito deferred OK if closed beta)

---

## 1. Account & IAM bootstrap

- [ ] Create (or select) AWS account for `staging` / `prod`
- [ ] Enable CloudTrail (multi-region) → S3 log bucket
- [ ] Enable GuardDuty (recommended)
- [ ] Create GitHub OIDC identity provider in IAM
- [ ] Create deploy role for GitHub Actions (least privilege: ECR push, ECS update, pass role)
- [ ] Create human break-glass admin role (MFA)
- [ ] Tagging standard: `Project=InvoiceGenie`, `Env=staging|prod`, `Owner=...`

---

## 2. Network

- [ ] VPC CIDR planned (e.g. `10.0.0.0/16`)
- [ ] 3 AZs: public + private app + private data subnets
- [ ] Internet Gateway + public routes
- [ ] NAT Gateway (1 for pilot cost; 1 per AZ for prod HA)
- [ ] Optional: VPC endpoints for `ecr.api`, `ecr.dkr`, `logs`, `secretsmanager`, `s3`
- [ ] VPC Flow Logs → CloudWatch or S3
- [ ] Security groups drafted:
  - [ ] `alb-sg` (443 in)
  - [ ] `web-sg` (3000 from alb)
  - [ ] `api-sg` (8080 from alb + web)
  - [ ] `rds-sg` (5432 from api only)

---

## 3. Secrets & parameters

Create in **Secrets Manager** (names illustrative):

- [ ] `invoicegenie/{env}/db` → username, password, host, port, dbname  
- [ ] `invoicegenie/{env}/api-keys` → `INVOICEGENIE_API_KEYS` map (`key:tenantUuid,...`)  
- [ ] `invoicegenie/{env}/jwt` → `INVOICEGENIE_JWT_SECRET` (if jwt mode)  

SSM (optional non-secrets):

- [ ] `/invoicegenie/{env}/outbox/kafka_enabled` = `false`
- [ ] `/invoicegenie/{env}/log/json` = `true`

Generate strong values:

- [ ] Postgres password ≥ 24 random chars  
- [ ] API keys not equal to compose `dev-local-key` in prod  
- [ ] JWT secret ≥ 32 bytes if used  

---

## 4. Data plane (RDS)

- [ ] Subnet group on **private data** subnets only
- [ ] Parameter group: `rds.force_ssl=1` (and connection limits sized for pool × tasks)
- [ ] Create PostgreSQL **15** instance
  - [ ] Staging: Single-AZ acceptable
  - [ ] Prod: **Multi-AZ**
- [ ] Storage encrypted with KMS CMK
- [ ] Backup retention ≥ 7 days (prod 14–35 preferred)
- [ ] Deletion protection **on** for prod
- [ ] Public access **No**
- [ ] SG: only `api-sg` on 5432
- [ ] Create app database `invoicegenie` and app user (not master for runtime)
- [ ] Note endpoint for JDBC:  
  `jdbc:postgresql://<endpoint>:5432/invoicegenie?sslmode=require`
- [ ] Snapshot after empty create (baseline)

---

## 5. Container registry

- [ ] ECR repository `invoicegenie-api`
- [ ] ECR repository `invoicegenie-web`
- [ ] Image scan on push enabled
- [ ] Lifecycle policy: retain last N images + any `prod-*` tags

Local/CI build smoke:

```bash
docker build -f Dockerfile.prod -t invoicegenie-api:local .
docker build -f web/Dockerfile -t invoicegenie-web:local ./web
```

- [ ] API image builds
- [ ] Web image builds

---

## 6. Load balancing & TLS

- [ ] Request ACM certificate for hostname(s); DNS validate
- [ ] Create ALB in public subnets
- [ ] HTTPS listener 443 with ACM cert
- [ ] HTTP 80 → redirect HTTPS
- [ ] Target group **API**: IP mode, port 8080, health `/q/health`, interval 30s, healthy threshold 2, unhealthy 3, grace ≥ 60s
- [ ] Target group **Web**: IP mode, port 3000, health `/` (or dedicated health if added)
- [ ] Listener rules:
  - [ ] `/api/*` → API
  - [ ] `/q/health` → API
  - [ ] `/q/swagger-ui*` → fixed 404
  - [ ] `/q/openapi*` → fixed 404
  - [ ] default → Web
- [ ] (Optional) CloudFront distribution + WAF association
- [ ] Route 53 alias → CloudFront or ALB

---

## 7. ECS / Fargate

- [ ] ECS cluster `invoicegenie-{env}`
- [ ] CloudWatch log groups:
  - [ ] `/invoicegenie/{env}/api`
  - [ ] `/invoicegenie/{env}/web`
- [ ] Task execution role: pull ECR, write logs, read secrets
- [ ] Task role API: minimal (Secrets read if app SDK later; usually execution role injects env)
- [ ] Service Connect or Cloud Map name for API (for `BACKEND_URL`)

### 7.1 API task definition

- [ ] Image: ECR `invoicegenie-api:<sha>`
- [ ] Port 8080
- [ ] CPU/Mem: 1 vCPU / 2 GB (adjust)
- [ ] Env / secrets:

| Name | Source |
|------|--------|
| `QUARKUS_PROFILE` | `prod` |
| `QUARKUS_HTTP_HOST` | `0.0.0.0` |
| `QUARKUS_DATASOURCE_JDBC_URL` | secret/param |
| `QUARKUS_DATASOURCE_USERNAME` | secret |
| `QUARKUS_DATASOURCE_PASSWORD` | secret |
| `QUARKUS_FLYWAY_MIGRATE_AT_START` | `true` (or controlled job) |
| `INVOICEGENIE_SECURITY_ENABLED` | `true` |
| `INVOICEGENIE_SECURITY_MODE` | `api-key` or `jwt` |
| `INVOICEGENIE_API_KEYS` | secret |
| `INVOICEGENIE_JWT_SECRET` | secret or `none` |
| `INVOICEGENIE_SECURITY_ALLOW_OPENAPI` | `false` |
| `OUTBOX_KAFKA_ENABLED` | `false` |
| `QUARKUS_LOG_CONSOLE_JSON` | `true` |

- [ ] Health check aligned with ALB (`/q/health`)
- [ ] Desired count staging: 1–2; prod: ≥ 2
- [ ] Deployment circuit breaker + rollback **enabled**

### 7.2 Web task definition

- [ ] Image: ECR `invoicegenie-web:<sha>`
- [ ] Port 3000
- [ ] CPU/Mem: 0.5 vCPU / 1 GB
- [ ] Env:

| Name | Value |
|------|--------|
| `BACKEND_URL` | `http://<api-service-connect>:8080` (preferred) or internal ALB URL |
| `NEXT_PUBLIC_APP_NAME` | `InvoiceGenie AR` |
| `NEXT_PUBLIC_ALLOW_TENANT_OVERRIDE` | **`false`** |
| `NEXT_PUBLIC_DEFAULT_TENANT_ID` | pilot tenant UUID |
| `NEXT_PUBLIC_API_KEY` | avoid in prod browser if possible; else short-lived process |

> Note: `NEXT_PUBLIC_*` are build-time for client bundles. Rebuild image if they change.

- [ ] Desired count staging: 1–2; prod: ≥ 2

---

## 8. WAF & edge hardening

- [ ] WAF WebACL with AWS Managed Rules (Common Rule Set, Known Bad Inputs)
- [ ] Rate-based rule on `/api/*` (tune threshold after baseline traffic)
- [ ] Associate WAF to CloudFront (preferred) or ALB
- [ ] Confirm OpenAPI/Swagger not reachable publicly
- [ ] Shield Standard active (default)

---

## 9. Observability & alarms

- [ ] Container Insights enabled on cluster
- [ ] ALB access logs → S3 (optional but recommended prod)
- [ ] Alarms:
  - [ ] ALB HTTP 5xx > threshold
  - [ ] Unhealthy host count > 0 for 2–3 periods
  - [ ] ECS CPU/Memory high
  - [ ] RDS free storage low
  - [ ] RDS CPU high
  - [ ] RDS connections high
- [ ] SNS topic → ops email/Slack
- [ ] Dashboard: request count, latency p99, 5xx, task count

---

## 10. First deploy sequence

1. [ ] Apply network + RDS + secrets (IaC or console)
2. [ ] Push API image to ECR
3. [ ] Create API service only (desired 1) — wait healthy
4. [ ] Verify logs: Flyway migrated V1–V6; no security misconfig crash
5. [ ] From bastion/ECS Exec or one-off task:  
   `curl -s http://localhost:8080/q/health`
6. [ ] API smoke with headers (replace values):

```bash
export BASE=https://<alb-or-dns>
export TENANT=00000000-0000-0000-0000-000000000001
export KEY=<api-key>

curl -s "$BASE/q/health"

curl -s -X POST "$BASE/api/v1/customers" \
  -H "Content-Type: application/json" \
  -H "X-Tenant-Id: $TENANT" \
  -H "X-API-Key: $KEY" \
  -d '{"customerCode":"AWS1","legalName":"AWS Smoke","currency":"USD"}'
```

7. [ ] Push web image; create web service
8. [ ] Open console in browser; Settings: tenant override **disabled**
9. [ ] Scale API/web to HA counts
10. [ ] Attach custom domain + final CloudFront

---

## 11. Security verification (must pass before customer data)

- [ ] `INVOICEGENIE_SECURITY_ENABLED=true` in running task
- [ ] Request **without** API key/JWT → **401/403**
- [ ] Wrong tenant vs key → **403**
- [ ] RDS not publicly reachable (`PubliclyAccessible=false`, NACL/SG check)
- [ ] No `NEXT_PUBLIC_ALLOW_TENANT_OVERRIDE=true` in prod image
- [ ] Secrets not printed in CloudWatch (spot-check logs)
- [ ] Security Hub / Trusted Advisor review (optional)
- [ ] Dependency scans green or risk-accepted (`.github/workflows/security.yml`)

---

## 12. Multi-tenant isolation checks

- [ ] Create resources under tenant A and tenant B
- [ ] List as A does not return B’s invoices/customers
- [ ] Direct ID access cross-tenant fails empty/404
- [ ] Confirm RLS GUC path still active (staging integration test)

---

## 13. Backup / restore drill (before go-live)

- [ ] Take manual snapshot
- [ ] Restore to new instance in staging VPC
- [ ] Point a temporary API task at restored DB
- [ ] Validate data + Flyway history
- [ ] Document restore time (RTO) and snapshot age (RPO)

---

## 14. CI/CD cutover

- [ ] Workflow builds and pushes ECR on `main`
- [ ] Staging auto-deploy
- [ ] Prod deploy requires environment approval
- [ ] Images referenced by **digest** or immutable tag `sha-...`
- [ ] Rollback tested once (previous task definition)

---

## 15. Go-live gate

- [ ] Staging soak ≥ 48h with smoke + manual AR flows (invoice issue, payment allocate)
- [ ] Runbooks reviewed: deploy, rollback, restore, incident ([design §12](./AWS_HOSTING_DESIGN.md#12-runbooks-outline))
- [ ] On-call / owner named
- [ ] DNS TTL lowered for cutover then restored
- [ ] Customer communication window (if replacing another system)
- [ ] Post-go-live: watch alarms 24–72h

---

## 16. Post-go-live (first week)

- [ ] Review WAF blocked counts (false positives)
- [ ] Right-size ECS CPU/memory from CloudWatch
- [ ] Confirm outbox table not unbounded (`cleanup-days` / cron)
- [ ] Schedule STORY-003 OIDC/RBAC milestone
- [ ] Schedule Quarkus LTS migration if not done

---

## Quick reference — ports & paths

| Path | Service | Public? |
|------|---------|---------|
| `/` | Web :3000 | Yes |
| `/api/*` | API :8080 | Yes (auth) |
| `/q/health` | API | Yes (no secrets) |
| `/q/metrics` | API | **No** (internal scrape only) |
| `/q/swagger-ui`, `/q/openapi` | API | **Blocked** in prod |
| Postgres 5432 | RDS | **Never public** |

---

## Sign-off

| Role | Name | Date | Signature |
|------|------|------|-----------|
| Engineering | | | |
| Ops / SRE | | | |
| Product / Security | | | |
