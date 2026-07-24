-- ============================================================================
-- V7: Webhook HTTP delivery log (STORY-009)
-- ============================================================================

CREATE TABLE IF NOT EXISTS ar_webhook_delivery (
    id                UUID PRIMARY KEY,
    tenant_id         UUID NOT NULL,
    subscription_id   UUID NOT NULL,
    outbox_id         UUID,
    event_type        VARCHAR(128) NOT NULL,
    url               VARCHAR(2048) NOT NULL,
    payload           TEXT,
    status            VARCHAR(32) NOT NULL,
    attempt_count     INT NOT NULL DEFAULT 0,
    http_status       INT,
    response_snippet  VARCHAR(2000),
    error_message     VARCHAR(2000),
    next_attempt_at   TIMESTAMPTZ,
    created_at        TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at        TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE INDEX IF NOT EXISTS idx_webhook_delivery_retry
    ON ar_webhook_delivery(status, next_attempt_at)
    WHERE status = 'RETRY';

CREATE INDEX IF NOT EXISTS idx_webhook_delivery_tenant
    ON ar_webhook_delivery(tenant_id, created_at DESC);

ALTER TABLE ar_webhook_delivery ENABLE ROW LEVEL SECURITY;

DROP POLICY IF EXISTS tenant_isolation_webhook_delivery ON ar_webhook_delivery;
CREATE POLICY tenant_isolation_webhook_delivery ON ar_webhook_delivery
    USING (tenant_id::text = current_setting('app.current_tenant_id', true));