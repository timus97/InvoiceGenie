-- ============================================================================
-- Idempotency store for safe retries (payment allocation, invoice issue, etc.)
-- ============================================================================

CREATE TABLE IF NOT EXISTS ar_idempotency (
    tenant_id           UUID NOT NULL REFERENCES ar_tenant(id),
    idempotency_key     VARCHAR(255) NOT NULL,
    request_hash        VARCHAR(128) NOT NULL,
    response_json       TEXT NOT NULL,
    created_at          TIMESTAMPTZ NOT NULL DEFAULT now(),

    PRIMARY KEY (tenant_id, idempotency_key)
);

CREATE INDEX IF NOT EXISTS idx_idempotency_tenant_created
    ON ar_idempotency(tenant_id, created_at DESC);

ALTER TABLE ar_idempotency ENABLE ROW LEVEL SECURITY;

CREATE POLICY tenant_isolation_idempotency ON ar_idempotency
    USING (tenant_id = current_setting('app.current_tenant_id', true)::uuid);
