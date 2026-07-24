# InvoiceGenie AR — Web GUI

Next.js 15 (App Router) + TypeScript + Tailwind console for the InvoiceGenie Quarkus AR API.

## Prerequisites

- **Node.js 20+** (22/24 fine)
- Backend running on **http://localhost:8080** (Quarkus `dev` profile recommended)

## Setup

```bash
cd web
cp .env.example .env.local   # Windows: copy .env.example .env.local
npm install
npm run dev
```

Open **http://localhost:3000**.

## How it talks to the backend

Next.js **rewrites** same-origin paths to Quarkus:

| Browser path | Proxied to |
|--------------|------------|
| `/api/*` | `BACKEND_URL/api/*` (default `http://localhost:8080`) |
| `/q/*` | `BACKEND_URL/q/*` (health, OpenAPI, Swagger) |

Every AR request sends **`X-Tenant-Id`** (from login session in prod, or Settings in dev) plus optional **`Authorization: Bearer`** / **`X-API-Key`** from the session stored in `sessionStorage`.

### Auth (STORY-003 Phase 2)

| Env | Purpose |
|-----|---------|
| `NEXT_PUBLIC_ALLOW_TENANT_OVERRIDE` | `true` (local demo) shows Settings tenant UUID field. **`false` in prod builds** — hide override, require `/login`. |
| `NEXT_PUBLIC_API_KEY` | Optional fallback only; prefer `/login` so keys are not baked into the client. |
| `BACKEND_URL` | Server-side rewrite target (not exposed to browser). |

Login calls `POST /api/v1/auth/login` with either:

- `{ "username", "password" }` → HS256 JWT (`accessToken`) + tenant/roles from server config
- `{ "apiKey" }` → tenant derived from server key map; JWT issued when JWT secret is configured

When override is false, tenant UUID cannot be free-form spoofed in the UI.

Default smoke tenant: `00000000-0000-0000-0000-000000000001`

## Scripts

| Command | Purpose |
|---------|---------|
| `npm run dev` | Dev server on :3000 (Turbopack) |
| `npm run build` | Production build |
| `npm run start` | Serve production build |
| `npm run lint` | ESLint |

## Layout

```
src/
  app/           # routes (dashboard, customers, invoices, …)
  components/    # shell, UI primitives, providers
  lib/api/       # fetch client + path helpers
  lib/           # tenant, money, errors, idempotency
  types/         # hand-written AR DTOs (OpenAPI gen later)
```

## Implementation phases

See the project GUI plan: PR-G0 scaffold → G1 Customers → G2 Invoices → G3 Payments → G4 Cheques → G5 Aging/credit notes/ledger → G6 polish (all implemented).