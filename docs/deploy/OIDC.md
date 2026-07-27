# OIDC / external JWT (PP-020)

InvoiceGenie keeps the default local auth stack (email/password login → HS256 JWT, API keys) and adds an **optional** OIDC resource-server path.

## Modes (`invoicegenie.security.mode` / `INVOICEGENIE_SECURITY_MODE`)

| Mode | Behavior |
|------|----------|
| `api-key` | `X-API-Key` (or Bearer raw key) only |
| `jwt` | Local HS256 JWT only (`invoicegenie.security.jwt.secret`) |
| `hybrid` | Local JWT **or** API key (default for web + M2M) |
| `oidc` | External IdP RS256 JWT validated via JWKS |
| `hybrid-oidc` | OIDC JWT → local HS256 JWT → API key |

Default for `%dev` remains security **enabled** with `hybrid` (no OIDC required). `%test` keeps security **disabled**.

## Config keys

```yaml
invoicegenie:
  security:
    mode: hybrid-oidc   # or oidc
    oidc:
      issuer: ${INVOICEGENIE_OIDC_ISSUER}
      audience: ${INVOICEGENIE_OIDC_AUDIENCE}   # optional; validated when set
      jwks-uri: ${INVOICEGENIE_OIDC_JWKS_URI}
      tenant-claim: tenant_id                   # claim carrying tenant UUID
      roles-claim: groups                       # groups or roles array
```

Env vars (see `.env.example`):

- `INVOICEGENIE_OIDC_ISSUER`
- `INVOICEGENIE_OIDC_JWKS_URI`
- `INVOICEGENIE_OIDC_AUDIENCE` (optional)
- `INVOICEGENIE_OIDC_TENANT_CLAIM` (default `tenant_id`)
- `INVOICEGENIE_OIDC_ROLES_CLAIM` (default `groups`)

## Token requirements

1. `alg` = **RS256**
2. `iss` must match configured issuer
3. `exp` not expired
4. Optional `aud` must match when audience is configured
5. Tenant UUID claim (`tenant_id` / configured name) **required**
6. Roles from `groups` and/or `roles` mapped to: `AR_CLERK`, `AR_CONTROLLER`, `AR_AUDITOR`, `TENANT_ADMIN`  
   Aliases: `clerk`, `controller`, `auditor`, `admin`

## Implementation notes

- Validator: `OidcJwtValidator` (JWKS fetch + cache, no `quarkus-oidc` dependency)
- Wired in `AuthFilter` when mode is `oidc` or `hybrid-oidc`
- `%prod` startup requires issuer + jwks-uri when mode is oidc/hybrid-oidc (`ProdSecurityValidator`)

## Web console

Browser login continues to use `/api/v1/auth/login` (local users) unless you front the SPA with an IdP and pass the IdP access token as `Authorization: Bearer` to the API. Full OIDC login redirect for the Next.js console is optional future work; **API bearer from IdP is supported today**.
