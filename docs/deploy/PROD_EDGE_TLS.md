# Production edge TLS & compose hardening (STORY-017)

> Pair with [PROJECT_STATUS.md](../PROJECT_STATUS.md), [AWS_DEPLOYMENT_CHECKLIST.md](../aws/AWS_DEPLOYMENT_CHECKLIST.md), and `ProdSecurityValidator`.

## Goals

1. Terminate TLS at an edge proxy (nginx sample or cloud LB).
2. Keep Postgres and app HTTP off the public internet.
3. Fail closed on production startup when security is off or demo secrets remain.
4. Document the **H2 packaged-jar limitation** (DEF-BE-007).

---

## Compose profile (local prod-like)

### Files

| File | Role |
|------|------|
| `docker-compose.yml` | Base stack: `app` (Dockerfile.prod), `web`, `postgres` |
| `docker-compose.prod.yml` | Overlay: nginx `edge` on 80/443, reset host ports for app/web/db, force prod security env |
| `docs/deploy/nginx-tls.conf` | TLS server block + OpenAPI blocked at edge |
| `.env` / `.env.example` | Secrets (never commit real values) |

### Run

```bash
cp .env.example .env
# Edit .env:
#   POSTGRES_PASSWORD=<strong>
#   QUARKUS_DATASOURCE_PASSWORD=<same strong>
#   INVOICEGENIE_API_KEYS=<strong-key>:<tenant-uuid>
#   # Do NOT use ar/ar, change-me-strong-password, or dev-local-key in prod

mkdir -p certs
# For local smoke only — use real certs (ACM, Let's Encrypt) in real environments:
openssl req -x509 -nodes -days 1 -newkey rsa:2048 \
  -keyout certs/privkey.pem -out certs/fullchain.pem \
  -subj "/CN=localhost"

docker compose -f docker-compose.prod.yml up -d --build
curl -k https://localhost/q/health
```

### Security defaults enforced by overlay

- `QUARKUS_PROFILE=prod`
- `INVOICEGENIE_SECURITY_ENABLED=true`
- `INVOICEGENIE_SECURITY_ALLOW_OPENAPI=false`
- `NEXT_PUBLIC_ALLOW_TENANT_OVERRIDE=false`
- Postgres / app / web **not** published to host ports (only edge 80/443)

### Startup validation (`ProdSecurityValidator`)

On any profile string containing `prod`, startup **aborts** when:

| Check | Failure |
|-------|---------|
| `invoicegenie.security.enabled` | false |
| API-key mode without keys | missing / `none` |
| Demo API keys | e.g. `dev-local-key` |
| JWT mode without secret or short secret | missing / &lt;16 chars / demo values |
| Datasource password | empty or known demo (`ar`, `password`, `change-me-strong-password`, …) |
| Classic `ar`/`ar` pair | refused |

Unit coverage: `ProdSecurityValidatorTest`.

---

## Kubernetes / cloud edge (outline)

Same principles; replace compose edge with platform LB:

```
Internet → Ingress / ALB (TLS 1.2+) → Service/web + Service/api
                                         ↓
                                      Postgres (private subnet only)
```

| Control | Recommendation |
|---------|----------------|
| TLS | ACM / cert-manager; redirect HTTP→HTTPS |
| Secrets | Secrets Manager / sealed-secrets / external-secrets — not ConfigMap plaintext |
| Probes | `GET /q/health` on API |
| NetworkPolicy | API→Postgres only; deny public Postgres |
| OpenAPI | Disabled in `%prod` (`swagger-ui` / `smallrye-openapi` off); block at edge too |
| Auth | `INVOICEGENIE_SECURITY_ENABLED=true` + strong keys/JWT |
| Flyway | `migrate-at-start=true` on deploy |

See AWS checklist for ECS/RDS wiring.

---

## DEF-BE-007 — Packaged jar cannot switch to H2 via runtime profile alone

| Run mode | H2 `dev` profile works? |
|----------|-------------------------|
| `mvn quarkus:dev -Dquarkus.profile=dev` | **Yes** (preferred local path) |
| Packaged `quarkus-run.jar` / `Dockerfile.prod` image with `-Dquarkus.profile=dev` | **No reliable switch** — image is built for Postgres JDBC + Flyway prod path |

**Ops implication:** smoke tests that need H2 must use `quarkus:dev` (or `%test`). Production images always target PostgreSQL. Do not document “flip profile on the jar” as an H2 escape hatch.

---

## CI smoke against prod-like config

Recommended gate (not required for every PR):

1. Start Postgres (Testcontainers or service container).
2. Run packaged app with `%prod`, strong random password, random API key.
3. Assert process starts (validator passes) and `/q/health` is 200.
4. Assert process **fails** when password=`ar` or security disabled (negative cases).

Local equivalent:

```bash
# Expect failure (default password):
QUARKUS_PROFILE=prod \
QUARKUS_DATASOURCE_PASSWORD=ar \
INVOICEGENIE_SECURITY_ENABLED=true \
INVOICEGENIE_API_KEYS=prod-key-abc:00000000-0000-0000-0000-000000000001 \
  java -jar ar-bootstrap/target/quarkus-app/quarkus-run.jar
# → IllegalStateException from ProdSecurityValidator
```

---

## Related

- Sample nginx: [nginx-tls.conf](./nginx-tls.conf)
- Compose base: `docker-compose.yml`
- Overlay: `docker-compose.prod.yml`
- Onboarding §8: [../ONBOARDING.md](../ONBOARDING.md)