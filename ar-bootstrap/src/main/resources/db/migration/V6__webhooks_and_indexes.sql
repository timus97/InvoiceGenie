-- ============================================================================
-- V6: Customer webhook subscriptions + supporting indexes
-- ============================================================================

CREATE TABLE IF NOT EXISTS ar_webhook_subscription (
    id              UUID PRIMARY KEY,
    tenant_id       UUID NOT NULL REFERENCES ar_tenant(id),
    url             VARCHAR(2048) NOT NULL,
    secret          VARCHAR(512),
    event_types     VARCHAR(512) NOT NULL DEFAULT '*',
    active          BOOLEAN NOT NULL DEFAULT true,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS idx_webhook_tenant_active
    ON ar_webhook_subscription(tenant_id, active);

ALTER TABLE ar_webhook_subscription ENABLE ROW LEVEL SECURITY;

DROP POLICY IF EXISTS tenant_isolation_webhook ON ar_webhook_subscription;
CREATE POLICY tenant_isolation_webhook ON ar_webhook_subscription
    USING (tenant_id::text = current_setting('app.current_tenant_id', true));