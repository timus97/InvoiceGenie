-- PP-025: AR posting period open/close (period close MVP)
CREATE TABLE IF NOT EXISTS ar_posting_period (
    id              UUID PRIMARY KEY,
    tenant_id       UUID NOT NULL REFERENCES ar_tenant(id),
    period_start    DATE NOT NULL,
    period_end      DATE NOT NULL,
    status          VARCHAR(16) NOT NULL DEFAULT 'OPEN',
    closed_at       TIMESTAMPTZ,
    closed_by       VARCHAR(255),
    notes           VARCHAR(512),
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT chk_posting_period_status CHECK (status IN ('OPEN', 'CLOSED')),
    CONSTRAINT chk_posting_period_range CHECK (period_end >= period_start),
    CONSTRAINT uq_posting_period_tenant_range UNIQUE (tenant_id, period_start, period_end)
);

CREATE INDEX IF NOT EXISTS idx_posting_period_tenant_status
    ON ar_posting_period (tenant_id, status);

CREATE INDEX IF NOT EXISTS idx_posting_period_tenant_dates
    ON ar_posting_period (tenant_id, period_start, period_end);
