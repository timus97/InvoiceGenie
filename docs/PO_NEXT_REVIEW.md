# Product Owner — Next Review Backlog

**Purpose:** Joint Eng + Product Owner prioritization before the next stakeholder review.  
**Date opened:** 2026-07-27  
**Owner:** Product Owner (priority) · Engineering Lead (sizing)

Use this doc in a PO sync. Check boxes when decided; move accepted items into a sprint board.

---

## 1. Current product posture (for PO)

| Area | Status | PO takeaway |
|------|--------|-------------|
| AR core (invoice → pay → ledger) | **Shipped** | Safe to demo finance ops |
| Operator console | **Shipped** | Day-to-day AR usable |
| Customer notifications pipeline | **Shipped** | Logging demo; real SMTP/Meta config for pilot |
| Collections polish (PDF, quiet hours, unsub) | **Shipped** | Professional collections cycle demoable |
| Enterprise SSO (OIDC) | **Shipped (API)** | Browser OIDC UX still lightweight login |
| Cloud production hosting | **Docs/IaC only** | Needs ops sprint for live staging |
| AP / full GL | **Not started** | Separate product investment |

---

## 2. Decisions needed from Product Owner

### D1 — Pilot channel strategy
- [ ] **Email-only pilot** (recommended first)
- [ ] Email + WhatsApp from day one
- [ ] Logging-only internal training (no customer contact)

### D2 — Identity
- [ ] Keep email/password + JWT for pilot
- [ ] Require OIDC (Azure AD / Cognito / Keycloak) before external users

### D3 — Pilot tenants
- [ ] Internal finance only
- [ ] 1–3 friendly external companies
- [ ] Multi-company shared service demo

### D4 — Scope freeze for pilot
Confirm **out of scope** until pilot metrics land:
- [ ] Accounts Payable module
- [ ] Full General Ledger product / financial statements
- [ ] Customer self-serve payment portal + PSP (Stripe etc.)
- [ ] Multi-region active-active

---

## 3. Upgrade candidates (existing features)

Rank: **H** = high ROI for pilot · **M** = next · **L** = later

| ID | Upgrade | Why | PO priority (fill) | Eng size |
|----|---------|-----|--------------------|----------|
| U-01 | Real SES domain + templates in staging | Customers actually get mail | H / M / L | M |
| U-02 | Meta WhatsApp approved templates live | Mobile-first collections | H / M / L | M |
| U-03 | DSO / collections KPI dashboard | Prove product ROI | H / M / L | M |
| U-04 | Recurring / subscription invoices | SaaS AR | H / M / L | L |
| U-05 | Bulk invoice import (CSV) | Migration from spreadsheets | H / M / L | M |
| U-06 | Payment link / PSP integration | Faster cash | H / M / L | L |
| U-07 | Dispute / promise-to-pay cases | Collections workbench | H / M / L | L |
| U-08 | Browser OIDC login UX | Enterprise IT | H / M / L | M |
| U-09 | Cross-currency FX P&L (full) | Global tenants | H / M / L | L |
| U-10 | Webhook admin: key rotation + redrive polish | Integrators | H / M / L | S |
| U-11 | Richer Playwright E2E + load smoke | Confidence for pilot | H / M / L | M |
| U-12 | Notification channel fallback policy UI | Reliability | H / M / L | S |
| U-13 | Invoice email branding (logo/colors) | Professionalism | H / M / L | M |
| U-14 | Collector assignment / work queues | Team scale | H / M / L | L |

---

## 4. Net-new feature ideas (for PO input)

| ID | Idea | Problem solved | Notes |
|----|------|----------------|-------|
| N-01 | Customer portal (view invoices, pay) | Self-serve AR | Depends on PSP choice |
| N-02 | Bank statement import / match | Cash app speed | Needs file format standards |
| N-03 | AI dunning copy / next-best-action | Higher collection rate | Optional; policy-gated |
| N-04 | Multi-entity consolidations | Shared services | Needs tenant hierarchy product |
| N-05 | Mobile-responsive collections PWA | Field collectors | FE investment |
| N-06 | AP module (bills, vendors) | Expand TAM | New bounded context |
| N-07 | Revenue recognition light | Controllers | After period close maturity |

---

## 5. Suggested next review agenda (60 min)

1. **10 min** — Demo recap (`docs/DEMO.md`)  
2. **15 min** — Decisions D1–D4  
3. **20 min** — Rank U-01…U-14 and pick top 5 for next 2 sprints  
4. **10 min** — Net-new ideas N-01…N-07 (park or promote)  
5. **5 min** — Success metrics for pilot (DSO, notify success %, zero cross-tenant incidents)

---

## 6. Proposed pilot success metrics (edit with PO)

| Metric | Target |
|--------|--------|
| Automated invoice notify success | ≥ 95% when email present |
| Cross-tenant incidents | 0 |
| Allocation integrity exceptions | 0 material |
| Clerk time vs spreadsheet | −30% (survey) |
| P1 defects open > 5 days | 0 |

---

## 7. PO notes (meeting scratchpad)

_Date: _________  
_Attendees: _________  

**Top 5 for next sprints:**

1. …
2. …
3. …
4. …
5. …

**Explicitly deferred:**

- …

**Open questions:**

- …
