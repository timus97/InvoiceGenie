# Quarkus LTS migration plan (P0-03)

> Status: **Planned** — platform remains on `3.8.6.1` (last 3.8 security patch).  
> Community 3.8 support ended February 2025. Target LTS: **3.27** (supported until ~Sep 2026) or **3.33** (current LTS).

## Why not in this PR

Moving 3.8 → 3.27/3.33 renames REST extensions (`quarkus-resteasy-reactive` → `quarkus-rest`), changes BOM coordinates for some extensions, and can break Jandex index versions. That deserves a dedicated PR with full regression.

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

## Acceptance

- [ ] All modules green on CI
- [ ] No remaining EOL Quarkus platform
- [ ] Dockerfile.prod builds against new platform
