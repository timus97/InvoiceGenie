# Quarkus LTS migration plan (P0-03)

> Status: **Done (STORY-004)** — platform on **`3.27.3`** LTS (2026-07-24).  
> Community 3.8 support ended February 2025. Migrated from `3.8.6.1` → `3.27.3`.

## Why not in this PR

Moving 3.8 → 3.27/3.33 renames REST extensions (`quarkus-resteasy-reactive` → `quarkus-rest`), changes BOM coordinates for some extensions, and can break Jandex index versions. That deserves a dedicated PR with full regression.

## Safe prep already in tree
- Jandex Maven plugin pinned to **3.1.6** (index v11) so 3.8 can read multi-module indexes.
- `%dev` / `%test` datasource credentials use `quarkus.datasource.username/password` (not under `jdbc.*`).
- No code relies on removed 3.8-only APIs beyond resteasy-reactive artifact names.

## Migration steps

1. Create branch `chore/quarkus-3.27-lts`.
2. Bump `quarkus.platform.version` in root `pom.xml`.
3. Replace artifacts:
   - `quarkus-resteasy-reactive` → `quarkus-rest`
   - `quarkus-resteasy-reactive-jackson` → `quarkus-rest-jackson`
4. Re-run `mvn -U clean test` module-by-module; fix CDI/JAX-RS breakages.
5. Confirm jandex plugin version compatible with platform.
6. Re-run OWASP scan; update suppression file if needed.
7. Smoke: issue invoice + allocate payment + health.

## What changed
- Root `quarkus.platform.version` = **3.27.3**
- `quarkus-resteasy-reactive` → `quarkus-rest`
- `quarkus-resteasy-reactive-jackson` → `quarkus-rest-jackson`
- `quarkus-smallrye-reactive-messaging-kafka` → `quarkus-messaging-kafka`
- Jandex plugin **3.2.3** (domain + api)

## Acceptance

- [x] All modules green on CI (`mvn clean test` BUILD SUCCESS)
- [x] No remaining EOL Quarkus platform
- [ ] Dockerfile.prod image build re-verified in CI (JVM package path unchanged)
