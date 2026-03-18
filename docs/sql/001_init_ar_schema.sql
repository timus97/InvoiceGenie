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
    status          VARCHAR(32) NOT NULL DEFAULT 'ACTIVE',
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
    currency        CHAR(3) NOT NULL,
    credit_limit    NUMERIC(19,2),
    payment_terms   VARCHAR(64),
    tax_id          VARCHAR(64),
    status          VARCHAR(32) NOT NULL DEFAULT 'ACTIVE',
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
    id                      UUID NOT NULL,
    tenant_id               UUID NOT NULL REFERENCES ar_tenant(id),
    invoice_number          VARCHAR(64) NOT NULL,
    version                 BIGINT NOT NULL DEFAULT 1,

    customer_id             UUID NOT NULL,
    customer_ref            VARCHAR(255),

    currency                CHAR(3) NOT NULL,
    exchange_rate_to_base   NUMERIC(18,8),

    issue_date              DATE NOT NULL,
    due_date                DATE NOT NULL,
    period_start            DATE,
    period_end              DATE,

    status                  VARCHAR(32) NOT NULL DEFAULT 'DRAFT',

    subtotal                NUMERIC(19,2) NOT NULL DEFAULT 0,
    tax_total               NUMERIC(19,2) NOT NULL DEFAULT 0,
    discount_total          NUMERIC(19,2) NOT NULL DEFAULT 0,
    total                   NUMERIC(19,2) NOT NULL DEFAULT 0,
    amount_due              NUMERIC(19,2) NOT NULL DEFAULT 0,

    notes                   TEXT,
    terms                   TEXT,
    metadata                JSONB NOT NULL DEFAULT '{}',

    created_at              TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at              TIMESTAMPTZ NOT NULL DEFAULT now(),
    issued_at               TIMESTAMPTZ,
    cancelled_at            TIMESTAMPTZ,

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

    snapshot            JSONB NOT NULL,
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
    tax_rate            NUMERIC(6,4),
    tax_amount          NUMERIC(19,2) NOT NULL DEFAULT 0,
    line_total          NUMERIC(19,2) NOT NULL,

    gl_account_code     VARCHAR(64),
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
    payment_number      VARCHAR(64) NOT NULL,

    customer_id         UUID NOT NULL,

    currency            CHAR(3) NOT NULL,
    amount              NUMERIC(19,2) NOT NULL,
    amount_unallocated  NUMERIC(19,2) NOT NULL DEFAULT 0,

    payment_date        DATE NOT NULL,
    received_at         TIMESTAMPTZ NOT NULL DEFAULT now(),

    method              VARCHAR(32) NOT NULL,
    reference           VARCHAR(128),
    bank_account_id     UUID,

    notes               TEXT,
    metadata            JSONB NOT NULL DEFAULT '{}',

    status              VARCHAR(32) NOT NULL DEFAULT 'RECEIVED',

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

    amount              NUMERIC(19,2) NOT NULL,
    currency            CHAR(3) NOT NULL,

    allocated_at        TIMESTAMPTZ NOT NULL DEFAULT now(),
    allocated_by        UUID,

    notes               TEXT,
    metadata            JSONB NOT NULL DEFAULT '{}',

    PRIMARY KEY (tenant_id, id),
    FOREIGN KEY (tenant_id, payment_id) REFERENCES ar_payment(tenant_id, id),
    FOREIGN KEY (tenant_id, invoice_id) REFERENCES ar_invoice(tenant_id, id),

    CONSTRAINT chk_allocation_positive CHECK (amount > 0),
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

    code                VARCHAR(64) NOT NULL,
    name                VARCHAR(255) NOT NULL,
    type                VARCHAR(32) NOT NULL,
    category            VARCHAR(32),

    currency            CHAR(3),

    is_system           BOOLEAN NOT NULL DEFAULT false,
    is_active           BOOLEAN NOT NULL DEFAULT true,

    parent_id           UUID,
    metadata            JSONB NOT NULL DEFAULT '{}',

    created_at          TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at          TIMESTAMPTZ NOT NULL DEFAULT now(),

    PRIMARY KEY (tenant_id, id),
    UNIQUE (tenant_id, code),
    FOREIGN KEY (tenant_id, parent_id) REFERENCES ar_account(tenant_id, id)
);

CREATE INDEX idx_account_tenant_type ON ar_account(tenant_id, type);

-- ============================================================================
-- LEDGER ENTRY (GL-style; single-sided or double-entry)
-- ============================================================================
CREATE TABLE ar_ledger_entry (
    id                  UUID NOT NULL,
    tenant_id           UUID NOT NULL REFERENCES ar_tenant(id),

    entry_number        VARCHAR(64) NOT NULL,
    entry_date          DATE NOT NULL,
    posting_date        DATE NOT NULL,

    account_id          UUID NOT NULL,
    customer_id         UUID,

    invoice_id          UUID,
    payment_id          UUID,
    allocation_id       UUID,

    debit               NUMERIC(19,2) NOT NULL DEFAULT 0,
    credit              NUMERIC(19,2) NOT NULL DEFAULT 0,

    currency            CHAR(3) NOT NULL,
    amount_base         NUMERIC(19,2),

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

    entity_type         VARCHAR(64) NOT NULL,
    entity_id           UUID,
    entity_ref          VARCHAR(128),

    action              VARCHAR(32) NOT NULL,
    actor_id            UUID,
    actor_type          VARCHAR(32),

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

    source              VARCHAR(64),

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

    aggregate_type      VARCHAR(64) NOT NULL,
    aggregate_id        UUID NOT NULL,
    event_type          VARCHAR(64) NOT NULL,
    payload             JSONB NOT NULL,

    created_at          TIMESTAMPTZ NOT NULL DEFAULT now(),
    published_at        TIMESTAMPTZ,
    status              VARCHAR(32) NOT NULL DEFAULT 'PENDING',

    PRIMARY KEY (tenant_id, id)
);

CREATE INDEX idx_outbox_tenant_status ON ar_outbox(tenant_id, status, created_at);

-- ============================================================================
-- ROW LEVEL SECURITY (RLS)
-- ============================================================================

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
