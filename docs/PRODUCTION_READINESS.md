# Production Readiness & Machine Verification — InvoiceGenie

> **Date:** 2026-07-20  
> **Machine audited:** `TIMUS` (Windows 10/11 Home Single Language, build 10.0.26200)  
> **Repo:** InvoiceGenie (multi-tenant AR backend + Next.js web)

---

## 1. Production system requirements

### 1.1 Runtime (application)

| Component | Production requirement | Notes |
|-----------|------------------------|-------|
| **JDK** | **17+** (17 LTS recommended; 21 supported by modern Quarkus) | Project compiles with `maven.compiler.source/target=17` |
| **Maven** | **3.9+** recommended | Used to build multi-module reactor |
| **OS** | Linux preferred for servers; Windows/macOS for ops tooling | Containers abstract most OS deps |
| **CPU** | 2+ vCPU (app), 1+ vCPU (web), 2+ vCPU (Postgres) | Scale with concurrent tenants |
| **RAM** | **4 GB+** JVM heap headroom for app; **512 MB–1 GB** for Next.js; **2 GB+** Postgres | Total host **8 GB+** recommended for all-in-one |
| **Disk** | **20 GB+** free for images, logs, DB volume | Plus backup volume |
| **PostgreSQL** | **15+** | Default profile; apply SQL migrations |
| **Kafka** | Optional today (`outbox.kafka-enabled=false`) | Required when enabling real event publish |
| **Node.js** | **20+** (build web image / SSR) | Production may serve static/standalone Next |
| **Container runtime** | Docker 24+ / containerd + Compose v2 | Or Kubernetes 1.28+ |
| **Reverse proxy** | nginx / Traefik / cloud LB with **TLS** | Not bundled |
| **Network** | Ports **8080** (API), **3000** (web), **5432** (DB, private only) | Do not expose Postgres publicly |

### 1.2 Configuration (must set in production)

| Setting | Dev default | Production expectation |
|---------|-------------|------------------------|
| `QUARKUS_DATASOURCE_JDBC_URL` | localhost Postgres / H2 | Managed Postgres URL |
| `QUARKUS_DATASOURCE_USERNAME` / `PASSWORD` | `ar` / `ar` | Strong secrets from vault |
| `quarkus.hibernate-orm.database.generation` | `none` (default) / `update` (dev) | **`none`** + migrations only |
| `outbox.kafka-enabled` | `false` | `true` only with Kafka + sender bean |
| `BACKEND_URL` (web) | `http://localhost:8080` | Internal service URL |
| `NEXT_PUBLIC_ALLOW_TENANT_OVERRIDE` | `true` in compose | **`false`** |
| Auth | None (header tenant only) | OIDC/JWT required before public exposure |
| Logging | File under `logs/` | Centralized (stdout → aggregator) |

### 1.3 Security baseline (production)

- [ ] Authentication (OIDC/JWT) — **not implemented**
- [ ] Authorization / roles — **not implemented**
- [ ] TLS termination — **ops responsibility**
- [ ] Secrets not in git / compose defaults — **needs work**
- [ ] Dependency scanning in CI — **scripts added; CI not yet**
- [ ] Postgres not publicly reachable
- [ ] Swagger/OpenAPI disabled or protected in prod
- [ ] Tenant isolation proven with tests (app filter + RLS)

### 1.4 Build & deploy artifacts

| Artifact | How to produce | Production-ready? |
|----------|----------------|-------------------|
| `ar-bootstrap` Quarkus app | `mvn -pl ar-bootstrap -am package` → `target/quarkus-app/` | **Yes** (JVM mode) |
| Root `Dockerfile` | Arena/eval base image | **No** — not a prod image |
| `web/Dockerfile` | Next.js image | Review for standalone/prod flags |
| `docker-compose.yml` | Local full stack | Dev/demo only (weak passwords, tenant override) |
| SQL | `docs/sql/001_init_ar_schema.sql`, `002_idempotency.sql` | Manual apply — automate |

---

## 2. Verification of this machine

### 2.1 Toolchain checklist

| Requirement | Required | Detected on TIMUS | Status |
|-------------|----------|-------------------|--------|
| JDK 17+ | Yes (build/run) | **JDK 17.0.5** via `JAVA_HOME`; **JDK 21.0.11** on `PATH` (`java`) | **PASS** (Maven uses 17) — see note |
| Maven 3.9+ | Yes | **Apache Maven 3.8.5** | **WARN** — works but below docs recommendation |
| Node.js 20+ | Yes (web) | **v24.18.0** | **PASS** |
| npm | Yes | **11.16.0** | **PASS** |
| Git | Yes | **2.52.0** | **PASS** |
| Docker / Compose | Recommended | Docker **29.5.3-rd** (Rancher Desktop), Compose **v5.1.4** installed | **FAIL runtime** — daemon not running |
| curl / HTTP client | Optional | Available via OS / tools | PASS enough |
| Kafka | Optional | Not required for current defaults | N/A |
| PostgreSQL client | Optional | Via Docker image when daemon up | Deferred |

### 2.2 Hardware resources

| Resource | Detected | Production all-in-one guidance | Status |
|----------|----------|--------------------------------|--------|
| CPU | Intel i7-10750H, **12** logical processors | ≥ 4 cores | **PASS** |
| RAM | **~16 GB** (16,993,009,664 bytes) | ≥ 8 GB | **PASS** |
| Free disk (C:) | **~361 GB** free | ≥ 20 GB | **PASS** |
| Arch | **amd64** | amd64/arm64 | **PASS** |

### 2.3 Runtime nuances on this machine

1. **Java split-brain:**  
   - `java -version` → Temurin **21**  
   - `JAVA_HOME` / Maven → Oracle **17.0.5**  
   - **Impact:** Builds are consistent with project target 17. Ad-hoc `java -jar` may use 21 (usually fine). Prefer aligning PATH and `JAVA_HOME` to one JDK for less confusion.

2. **Docker daemon down:**  
   - CLI present (Rancher Desktop) but `docker info` fails: cannot connect to `docker_engine` pipe.  
   - **Impact:** Cannot run `docker compose up`, Postgres container, or image builds until Rancher Desktop / Docker is started.

3. **Maven version:** 3.8.5 is slightly below README “3.9+”. Most goals work; upgrade if plugin compatibility issues appear.

4. **Local run without Docker:** Fully supported via H2 `dev` profile:

   ```powershell
   mvn -pl ar-bootstrap "-Dquarkus.profile=dev" "-Dquarkus.kafka.devservices.enabled=false" quarkus:dev
   cd web; npm run dev
   ```

### 2.4 Capability matrix for *this machine*

| Goal | Ready now? | What to do |
|------|------------|------------|
| Compile backend | **Yes** | `mvn clean install` (with JDK 17 via Maven) |
| Unit/integration tests (H2) | **Yes** | `mvn test` |
| Run API locally (H2) | **Yes** | `dev` profile scripts |
| Run Web GUI locally | **Yes** | Node 24 + `npm run dev` |
| Postgres-like local stack | **No (until Docker starts)** | Start Rancher Desktop, then `docker compose up -d postgres` |
| Full compose (app+web+db) | **No** | Fix prod Dockerfile + start Docker; treat current compose as demo |
| Production deploy from this laptop | **Not recommended as prod host** | Use as **dev workstation**; deploy to Linux VM/K8s with secrets, TLS, auth |
| Security scan | **Partial** | `./scripts/security-scan.ps1 -ReportOnly` (first NVD sync is slow without API key) |
| npm audit gate | **Yes** | `cd web; npm run audit:ci` |

### 2.5 Overall machine verdict

| Category | Verdict |
|----------|---------|
| **Development (H2 + Next.js)** | **READY** |
| **Integration with Postgres via Docker** | **BLOCKED** until Docker daemon is started |
| **Production hosting on this PC** | **NOT SUITABLE** as a production environment (Windows Home, no TLS/auth stack, demo secrets, desktop Docker) |
| **Production *build* from this PC** | **MOSTLY READY** after Docker is up and a real production Dockerfile is added (see P0-04 in feature backlog) |

---

## 3. Minimum steps to make *local* prod-like validation work here

1. Start **Rancher Desktop** (enable Docker/container engine).  
2. Align Java: set `PATH` so `java` matches `JAVA_HOME` (17 or 21 consistently).  
3. Optional: upgrade Maven to **3.9+**.  
4. Apply schema:

   ```powershell
   docker compose up -d postgres
   # apply docs/sql/001_init_ar_schema.sql and 002_idempotency.sql
   ```

5. Run API default profile against Postgres; run web with `NEXT_PUBLIC_ALLOW_TENANT_OVERRIDE=false` for a stricter demo.  
6. Run security scans:

   ```powershell
   ./scripts/security-scan.ps1 -ReportOnly
   cd web; npm run audit:all
   ```

---

## 4. Production go-live checklist (environment-agnostic)

- [ ] Supported Quarkus LTS (post-3.8 migration)  
- [ ] AuthN/AuthZ live  
- [ ] Managed Postgres with backups & migrations  
- [ ] Production container image (not arena Dockerfile)  
- [ ] Secrets via vault / platform secrets  
- [ ] TLS + private DB network  
- [ ] Health/readiness probes wired in orchestrator  
- [ ] Dependency CVE scan in CI (fail on High+)  
- [ ] Load test smoke on invoice + payment allocation  
- [ ] Runbook: backup, restore, tenant isolation incident  

---

## 5. Related docs

- [FEATURE_PRIORITY_BACKLOG.md](./FEATURE_PRIORITY_BACKLOG.md) — prioritized gaps  
- [ONBOARDING.md](./ONBOARDING.md) — architecture  
- [SCHEMA.md](./SCHEMA.md) — data model  
- [../README.md](../README.md) — quick start  

*Machine facts captured from live shell checks on 2026-07-20.*
