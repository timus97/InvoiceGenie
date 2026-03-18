# InvoiceGenie AR Database Schema

**Database:** PostgreSQL 15+  
**Multi-tenancy:** Single DB, `tenant_id` column + RLS  
**IDs:** UUID v7 (time-ordered)  
**Currency:** ISO 4217 (3-char codes)  
**Principles:** DDD aggregates, audit-first, payment allocation across invoices

---

## Design Decisions

| Decision | Choice | Rationale |
|----------|--------|-----------|
| **ID Strategy** | UUID v7 (time-ordered) | No central bottleneck; good for partitioning; sortable by creation time |
| **Tenant Storage** | Single DB, `tenant_id UUID NOT NULL` on every table | Simpler ops, one schema, easier backups vs schema-per-tenant |
| **Tenant Isolation** | App-level filter + RLS policy | Defence in depth; even buggy SQL can't leak |
| **Invoice Versioning** | Separate `ar_invoice_version` table (full snapshot per version) | Audit trail; no mutation loss; optional diff-based storage later |
| **Payment Allocation** | `ar_payment_allocation` (many-to-many: payment ↔ invoice) | Supports partial payments, overpayments, multi-invoice allocation |
| **Ledger** | `ar_ledger_entry` with `account_id` reference | GL-ready; AR publishes events, GL subscribes (future) |
| **Audit Log** | `ar_audit_log` with `entity_type`, `entity_id`, `action`, `before/after JSONB` | Unified audit; JSONB for flexible before/after snapshots |
| **Money Precision** | `NUMERIC(19,2)` for monetary amounts | Standard 2-decimal; ISO 4217 currencies |
| **Exchange Rates** | `ar_exchange_rate` for cross-currency reporting | Base currency per tenant; optional; not required for invoice currency |

---

## Schema (PostgreSQL)

```sql
-- ============================================================================
-- InvoiceGenie AR Schema — PostgreSQL 15+
-- Multi-tenant, multi-currency, payment allocation, audit-first
-- ============================================================================

-- Enable extensions
CREATE EXTENSION IF NOT EXISTS "uuid-ossp";

-- ============================================================================
-- TENANT
-- ============================================================================
CREATE TABLE ar_tenant (
    id              UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    code            VARCHAR(64) NOT NULL UNIQUE,
    name            VARCHAR(255) NOT NULL,
    base_currency   CHAR(3) NOT NULL DEFAULT 'USD',
    status          VARCHAR(32) NOT NULL DEFAULT 'ACTIVE', -- ACTIVE, SUSPENDED, DELETED
    settings        JSONB NOT NULL DEFAULT '{}',
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX idx_tenant_code ON ar_tenant(code);

-- ============================================================================
-- CUSTOMER (AR Customer)
-- ============================================================================
CREATE TABLE ar_customer (
    id              UUID NOT NULL,
    tenant_id       UUID NOT NULL REFERENCES ar_tenant(id),
    customer_code   VARCHAR(64) NOT NULL,
    legal_name      VARCHAR(255) NOT NULL,
    display_name    VARCHAR(255),
    email           VARCHAR(255),
    phone           VARCHAR(64),
    billing_address JSONB,
    currency        CHAR(3) NOT NULL, -- default currency for invoices
    credit_limit    NUMERIC(19,2),
    payment_terms   VARCHAR(64), -- e.g., NET30, NET60, IMMEDIATE
    tax_id          VARCHAR(64),
    status          VARCHAR(32) NOT NULL DEFAULT 'ACTIVE', -- ACTIVE, BLOCKED, DELETED
    metadata        JSONB NOT NULL DEFAULT '{}',
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    version         BIGINT NOT NULL DEFAULT 1,

    PRIMARY KEY (tenant_id, id),
    UNIQUE (tenant_id, customer_code)
);

CREATE INDEX idx_customer_tenant_code ON ar_customer(tenant_id, customer_code);
CREATE INDEX idx_customer_tenant_status ON ar_customer(tenant_id, status);

-- ============================================================================
-- INVOICE (Current State / Aggregate Root)
-- ============================================================================
CREATE TABLE ar_invoice (
    id                  UUID NOT NULL,
    tenant_id           UUID NOT NULL REFERENCES ar_tenant(id),
    invoice_number      VARCHAR(64) NOT NULL, -- human-readable, tenant-scoped
    version             BIGINT NOT NULL DEFAULT 1, -- incremented on update

    customer_id         UUID NOT NULL,
    customer_ref        VARCHAR(255), -- denormalized display name or code

    currency            CHAR(3) NOT NULL,
    exchange_rate_to_base NUMERIC(18,8), -- optional, for cross-currency reporting

    issue_date          DATE NOT NULL,
    due_date            DATE NOT NULL,
    period_start        DATE,
    period_end          DATE,

    status              VARCHAR(32) NOT NULL DEFAULT 'DRAFT',
        -- DRAFT, ISSUED, PARTIALLY_PAID, PAID, OVERDUE, WRITTEN_OFF

    subtotal            NUMERIC(19,2) NOT NULL DEFAULT 0,
    tax_total           NUMERIC(19,2) NOT NULL DEFAULT 0,
    discount_total      NUMERIC(19,2) NOT NULL DEFAULT 0,
    total               NUMERIC(19,2) NOT NULL DEFAULT 0,
    amount_due          NUMERIC(19,2) NOT NULL DEFAULT 0, -- total - sum(allocations)

    notes               TEXT,
    terms               TEXT,
    metadata            JSONB NOT NULL DEFAULT '{}',

    created_at          TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at          TIMESTAMPTZ NOT NULL DEFAULT now(),
    issued_at           TIMESTAMPTZ,
    written_off_at      TIMESTAMPTZ,

    PRIMARY KEY (tenant_id, id),
    UNIQUE (tenant_id, invoice_number),
    FOREIGN KEY (tenant_id, customer_id) REFERENCES ar_customer(tenant_id, id)
);

CREATE INDEX idx_invoice_tenant_customer ON ar_invoice(tenant_id, customer_id);
CREATE INDEX idx_invoice_tenant_status_due ON ar_invoice(tenant_id, status, due_date);
CREATE INDEX idx_invoice_tenant_created ON ar_invoice(tenant_id, created_at DESC);
CREATE INDEX idx_invoice_tenant_invoice_number ON ar_invoice(tenant_id, invoice_number);

-- ============================================================================
-- INVOICE VERSION (Full Snapshot History)
-- ============================================================================
CREATE TABLE ar_invoice_version (
    id                  UUID NOT NULL,
    tenant_id           UUID NOT NULL REFERENCES ar_tenant(id),
    invoice_id          UUID NOT NULL,
    version             BIGINT NOT NULL,

    snapshot            JSONB NOT NULL, -- full invoice + lines snapshot
    changed_by          UUID,
    change_reason       VARCHAR(255),
    created_at          TIMESTAMPTZ NOT NULL DEFAULT now(),

    PRIMARY KEY (tenant_id, invoice_id, version),
    FOREIGN KEY (tenant_id, invoice_id) REFERENCES ar_invoice(tenant_id, id)
);

CREATE INDEX idx_invoice_version_tenant_invoice ON ar_invoice_version(tenant_id, invoice_id, version DESC);

-- ============================================================================
-- INVOICE LINE
-- ============================================================================
CREATE TABLE ar_invoice_line (
    id                  UUID NOT NULL,
    tenant_id           UUID NOT NULL REFERENCES ar_tenant(id),
    invoice_id          UUID NOT NULL,
    sequence            INT NOT NULL,

    description         TEXT NOT NULL,
    quantity            NUMERIC(18,4) NOT NULL DEFAULT 1,
    unit_price          NUMERIC(19,4) NOT NULL,
    discount_amount     NUMERIC(19,2) NOT NULL DEFAULT 0,
    tax_rate            NUMERIC(6,4), -- e.g., 0.0825 for 8.25%
    tax_amount          NUMERIC(19,2) NOT NULL DEFAULT 0,
    line_total          NUMERIC(19,2) NOT NULL,

    gl_account_code     VARCHAR(64), -- optional GL mapping
    metadata            JSONB NOT NULL DEFAULT '{}',

    PRIMARY KEY (tenant_id, invoice_id, sequence),
    FOREIGN KEY (tenant_id, invoice_id) REFERENCES ar_invoice(tenant_id, id)
);

CREATE INDEX idx_invoice_line_tenant_invoice ON ar_invoice_line(tenant_id, invoice_id);

-- ============================================================================
-- PAYMENT (Payment Received)
-- ============================================================================
CREATE TABLE ar_payment (
    id                  UUID NOT NULL,
    tenant_id           UUID NOT NULL REFERENCES ar_tenant(id),
    payment_number      VARCHAR(64) NOT NULL, -- human-readable, tenant-scoped

    customer_id         UUID NOT NULL,

    currency            CHAR(3) NOT NULL,
    amount              NUMERIC(19,2) NOT NULL,
    amount_unallocated  NUMERIC(19,2) NOT NULL DEFAULT 0, -- amount - sum(allocations)

    payment_date        DATE NOT NULL,
    received_at         TIMESTAMPTZ NOT NULL DEFAULT now(),

    method              VARCHAR(32) NOT NULL, -- BANK_TRANSFER, CARD, CASH, CHECK, OTHER
    reference           VARCHAR(128), -- bank ref, check number, etc.
    bank_account_id     UUID, -- optional, links to tenant's bank account

    notes               TEXT,
    metadata            JSONB NOT NULL DEFAULT '{}',

    status              VARCHAR(32) NOT NULL DEFAULT 'RECEIVED', -- RECEIVED, REVERSED, REFUNDED

    created_at          TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at          TIMESTAMPTZ NOT NULL DEFAULT now(),

    PRIMARY KEY (tenant_id, id),
    UNIQUE (tenant_id, payment_number),
    FOREIGN KEY (tenant_id, customer_id) REFERENCES ar_customer(tenant_id, id)
);

CREATE INDEX idx_payment_tenant_customer ON ar_payment(tenant_id, customer_id);
CREATE INDEX idx_payment_tenant_date ON ar_payment(tenant_id, payment_date DESC);
CREATE INDEX idx_payment_tenant_status ON ar_payment(tenant_id, status);

-- ============================================================================
-- PAYMENT ALLOCATION (Payment ↔ Invoice many-to-many)
-- ============================================================================
CREATE TABLE ar_payment_allocation (
    id                  UUID NOT NULL,
    tenant_id           UUID NOT NULL REFERENCES ar_tenant(id),

    payment_id          UUID NOT NULL,
    invoice_id          UUID NOT NULL,

    amount              NUMERIC(19,2) NOT NULL, -- amount allocated from this payment to this invoice
    currency            CHAR(3) NOT NULL, -- must match both payment and invoice currency

    allocated_at        TIMESTAMPTZ NOT NULL DEFAULT now(),
    allocated_by        UUID, -- user id or system

    notes               TEXT,
    metadata            JSONB NOT NULL DEFAULT '{}',

    PRIMARY KEY (tenant_id, id),
    FOREIGN KEY (tenant_id, payment_id) REFERENCES ar_payment(tenant_id, id),
    FOREIGN KEY (tenant_id, invoice_id) REFERENCES ar_invoice(tenant_id, id),

    CONSTRAINT chk_allocation_positive CHECK (amount > 0),
    -- Ensure allocation currencies match (enforced at app layer; DB can add trigger if needed)
    UNIQUE (tenant_id, payment_id, invoice_id)
);

CREATE INDEX idx_allocation_tenant_payment ON ar_payment_allocation(tenant_id, payment_id);
CREATE INDEX idx_allocation_tenant_invoice ON ar_payment_allocation(tenant_id, invoice_id);

-- ============================================================================
-- ACCOUNT (Chart of Accounts — AR subset)
-- ============================================================================
CREATE TABLE ar_account (
    id                  UUID NOT NULL,
    tenant_id           UUID NOT NULL REFERENCES ar_tenant(id),

    code                VARCHAR(64) NOT NULL, -- e.g., AR, CASH, REVENUE, TAX_PAYABLE
    name                VARCHAR(255) NOT NULL,
    type                VARCHAR(32) NOT NULL, -- ASSET, LIABILITY, REVENUE, EXPENSE, EQUITY
    category            VARCHAR(32), -- e.g., CURRENT_ASSET, DEFERRED_REVENUE

    currency            CHAR(3), -- NULL means base currency

    is_system           BOOLEAN NOT NULL DEFAULT false, -- system-managed accounts
    is_active           BOOLEAN NOT NULL DEFAULT true,

    parent_id           UUID, -- for hierarchy
    metadata            JSONB NOT NULL DEFAULT '{}',

    created_at          TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at          TIMESTAMPTZ NOT NULL DEFAULT now(),

    PRIMARY KEY (tenant_id, id),
    UNIQUE (tenant_id, code),
    FOREIGN KEY (tenant_id, parent_id) REFERENCES ar_account(tenant_id, id)
);

CREATE INDEX idx_account_tenant_type ON ar_account(tenant_id, type);

-- Seed default AR accounts per tenant (via migration or app)
-- Example: AR Receivable, Cash, Revenue, Tax Payable, Discounts, Bad Debt

-- ============================================================================
-- LEDGER ENTRY (GL-style; single-sided or double-entry)
-- ============================================================================
CREATE TABLE ar_ledger_entry (
    id                  UUID NOT NULL,
    tenant_id           UUID NOT NULL REFERENCES ar_tenant(id),

    entry_number        VARCHAR(64) NOT NULL, -- tenant-scoped sequence
    entry_date          DATE NOT NULL,
    posting_date        DATE NOT NULL,

    account_id          UUID NOT NULL,
    customer_id         UUID, -- optional, for AR aging

    invoice_id          UUID,
    payment_id          UUID,
    allocation_id       UUID, -- if tied to allocation

    debit               NUMERIC(19,2) NOT NULL DEFAULT 0,
    credit              NUMERIC(19,2) NOT NULL DEFAULT 0,

    currency            CHAR(3) NOT NULL,
    amount_base         NUMERIC(19,2), -- converted to base currency

    description         TEXT,
    reference           VARCHAR(128),

    metadata            JSONB NOT NULL DEFAULT '{}',

    created_at          TIMESTAMPTZ NOT NULL DEFAULT now(),
    created_by          UUID,

    PRIMARY KEY (tenant_id, id),
    FOREIGN KEY (tenant_id, account_id) REFERENCES ar_account(tenant_id, id),
    FOREIGN KEY (tenant_id, customer_id) REFERENCES ar_customer(tenant_id, id),
    FOREIGN KEY (tenant_id, invoice_id) REFERENCES ar_invoice(tenant_id, id),
    FOREIGN KEY (tenant_id, payment_id) REFERENCES ar_payment(tenant_id, id),
    FOREIGN KEY (tenant_id, allocation_id) REFERENCES ar_payment_allocation(tenant_id, id),

    CONSTRAINT chk_debit_or_credit CHECK (
        (debit > 0 AND credit = 0) OR (debit = 0 AND credit > 0)
    )
);

CREATE INDEX idx_ledger_tenant_account_date ON ar_ledger_entry(tenant_id, account_id, posting_date);
CREATE INDEX idx_ledger_tenant_customer_date ON ar_ledger_entry(tenant_id, customer_id, posting_date);
CREATE INDEX idx_ledger_tenant_invoice ON ar_ledger_entry(tenant_id, invoice_id);
CREATE INDEX idx_ledger_tenant_payment ON ar_ledger_entry(tenant_id, payment_id);

-- ============================================================================
-- AUDIT LOG
-- ============================================================================
CREATE TABLE ar_audit_log (
    id                  UUID NOT NULL DEFAULT gen_random_uuid(),
    tenant_id           UUID NOT NULL REFERENCES ar_tenant(id),

    entity_type         VARCHAR(64) NOT NULL, -- INVOICE, PAYMENT, CUSTOMER, etc.
    entity_id           UUID,
    entity_ref          VARCHAR(128), -- human-readable ref (e.g., invoice_number)

    action              VARCHAR(32) NOT NULL, -- CREATE, UPDATE, DELETE, STATUS_CHANGE, ALLOCATE, etc.
    actor_id            UUID, -- user or system
    actor_type          VARCHAR(32), -- USER, SYSTEM, API

    before_state        JSONB,
    after_state         JSONB,

    ip_address          INET,
    user_agent          TEXT,

    created_at          TIMESTAMPTZ NOT NULL DEFAULT now(),

    PRIMARY KEY (tenant_id, id)
);

CREATE INDEX idx_audit_tenant_entity ON ar_audit_log(tenant_id, entity_type, entity_id);
CREATE INDEX idx_audit_tenant_actor ON ar_audit_log(tenant_id, actor_id, created_at DESC);
CREATE INDEX idx_audit_tenant_time ON ar_audit_log(tenant_id, created_at DESC);

-- Partitioning hint: consider partitioning ar_audit_log by created_at (monthly) for high-volume tenants

-- ============================================================================
-- EXCHANGE RATE (Optional — for cross-currency reporting)
-- ============================================================================
CREATE TABLE ar_exchange_rate (
    id                  UUID NOT NULL DEFAULT gen_random_uuid(),
    tenant_id           UUID NOT NULL REFERENCES ar_tenant(id),

    from_currency       CHAR(3) NOT NULL,
    to_currency         CHAR(3) NOT NULL,
    rate                NUMERIC(18,8) NOT NULL,
    effective_date      DATE NOT NULL,

    source              VARCHAR(64), -- MANUAL, API, etc.

    PRIMARY KEY (tenant_id, id),
    UNIQUE (tenant_id, from_currency, to_currency, effective_date)
);

CREATE INDEX idx_exrate_tenant_currencies_date ON ar_exchange_rate(tenant_id, from_currency, to_currency, effective_date DESC);

-- ============================================================================
-- OUTBOX (Transactional Outbox for Domain Events)
-- ============================================================================
CREATE TABLE ar_outbox (
    id                  UUID NOT NULL DEFAULT gen_random_uuid(),
    tenant_id           UUID NOT NULL REFERENCES ar_tenant(id),

    aggregate_type      VARCHAR(64) NOT NULL, -- INVOICE, PAYMENT, etc.
    aggregate_id        UUID NOT NULL,
    event_type          VARCHAR(64) NOT NULL, -- InvoiceIssued, PaymentRecorded, etc.
    payload             JSONB NOT NULL,

    created_at          TIMESTAMPTZ NOT NULL DEFAULT now(),
    published_at        TIMESTAMPTZ,
    status              VARCHAR(32) NOT NULL DEFAULT 'PENDING', -- PENDING, PUBLISHED, FAILED

    PRIMARY KEY (tenant_id, id)
);

CREATE INDEX idx_outbox_tenant_status ON ar_outbox(tenant_id, status, created_at);

-- ============================================================================
-- ROW LEVEL SECURITY (RLS)
-- ============================================================================

-- Enable RLS on all tenant tables
ALTER TABLE ar_customer ENABLE ROW LEVEL SECURITY;
ALTER TABLE ar_invoice ENABLE ROW LEVEL SECURITY;
ALTER TABLE ar_invoice_version ENABLE ROW LEVEL SECURITY;
ALTER TABLE ar_invoice_line ENABLE ROW LEVEL SECURITY;
ALTER TABLE ar_payment ENABLE ROW LEVEL SECURITY;
ALTER TABLE ar_payment_allocation ENABLE ROW LEVEL SECURITY;
ALTER TABLE ar_account ENABLE ROW LEVEL SECURITY;
ALTER TABLE ar_ledger_entry ENABLE ROW LEVEL SECURITY;
ALTER TABLE ar_audit_log ENABLE ROW LEVEL SECURITY;
ALTER TABLE ar_exchange_rate ENABLE ROW LEVEL SECURITY;
ALTER TABLE ar_outbox ENABLE ROW LEVEL SECURITY;

-- RLS policy: tenant_id must match app.current_tenant_id session variable
-- Application sets this when obtaining a connection (see TenantFilter)

CREATE POLICY tenant_isolation_customer ON ar_customer
    USING (tenant_id = current_setting('app.current_tenant_id', true)::uuid);

CREATE POLICY tenant_isolation_invoice ON ar_invoice
    USING (tenant_id = current_setting('app.current_tenant_id', true)::uuid);

CREATE POLICY tenant_isolation_invoice_version ON ar_invoice_version
    USING (tenant_id = current_setting('app.current_tenant_id', true)::uuid);

CREATE POLICY tenant_isolation_invoice_line ON ar_invoice_line
    USING (tenant_id = current_setting('app.current_tenant_id', true)::uuid);

CREATE POLICY tenant_isolation_payment ON ar_payment
    USING (tenant_id = current_setting('app.current_tenant_id', true)::uuid);

CREATE POLICY tenant_isolation_allocation ON ar_payment_allocation
    USING (tenant_id = current_setting('app.current_tenant_id', true)::uuid);

CREATE POLICY tenant_isolation_account ON ar_account
    USING (tenant_id = current_setting('app.current_tenant_id', true)::uuid);

CREATE POLICY tenant_isolation_ledger ON ar_ledger_entry
    USING (tenant_id = current_setting('app.current_tenant_id', true)::uuid);

CREATE POLICY tenant_isolation_audit ON ar_audit_log
    USING (tenant_id = current_setting('app.current_tenant_id', true)::uuid);

CREATE POLICY tenant_isolation_exrate ON ar_exchange_rate
    USING (tenant_id = current_setting('app.current_tenant_id', true)::uuid);

CREATE POLICY tenant_isolation_outbox ON ar_outbox
    USING (tenant_id = current_setting('app.current_tenant_id', true)::uuid);

-- ============================================================================
-- CONSTRAINTS & TRIGGERS (Optional — enforced at app layer; DB can add)
-- ============================================================================

-- Trigger: prevent allocation sum > payment amount (application-enforced; add trigger if desired)
-- Trigger: prevent allocation currency mismatch
-- Trigger: auto-update invoice amount_due on allocation insert/delete

-- ============================================================================
-- INDEXES SUMMARY (Performance for Scale)
-- ============================================================================

-- All indexes lead with (tenant_id, ...) to leverage RLS and tenant-scoped queries.
-- For cursor pagination: (tenant_id, created_at, id) or (tenant_id, status, due_date)
-- For aging reports: (tenant_id, customer_id, status, due_date)
-- For ledger balance: (tenant_id, account_id, posting_date)
```

---

## Entity Relationship Summary

```
ar_tenant (1)
  ├── ar_customer (N)
  │     ├── ar_invoice (N)
  │     │     ├── ar_invoice_version (N)
  │     │     ├── ar_invoice_line (N)
  │     │     └── ar_ledger_entry (N)
  │     └── ar_payment (N)
  │           └── ar_payment_allocation (N) ──┐
  │                                             │
  └──── ar_account (N)                          │
          └── ar_ledger_entry (N) ◄─────────────┘

ar_audit_log (standalone, tenant-scoped)
ar_exchange_rate (standalone, tenant-scoped)
ar_outbox (standalone, tenant-scoped)
```

---

## Migration Strategy

- Use Flyway or Liquibase for migrations.
- First migration: create all tables, indexes, RLS policies.
- Seed default accounts per tenant on tenant creation (application layer).
- Add new columns with `ALTER TABLE ... ADD COLUMN` (nullable, default).
- Versioned invoice snapshots are immutable once written.

---

## Notes

- **Invoice versioning**: Every significant change (status change, line edit, re-issue) creates a new `ar_invoice_version` row with full `snapshot` JSONB.
- **Payment allocation**: One payment can allocate to N invoices; one invoice can receive allocations from N payments.
- **Ledger**: Single-sided entries; double-entry validation in application layer or via trigger.
- **Audit**: Every mutation should write to `ar_audit_log` via trigger or application service.
- **SQLite fallback**: For dev/Docker without PostgreSQL, skip RLS and use `tenant_id` filter only.
