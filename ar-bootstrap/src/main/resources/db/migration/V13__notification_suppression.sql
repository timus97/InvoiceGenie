-- ============================================================================
-- V13: Notification destination suppressions (hard bounce / complaint) — PP-003
-- ============================================================================

CREATE TABLE IF NOT EXISTS ar_notification_suppression (
    id                      UUID PRIMARY KEY,
    tenant_id               UUID NOT NULL REFERENCES ar_tenant(id),
    channel                 VARCHAR(32) NOT NULL,
    destination_normalized  VARCHAR(320) NOT NULL,
    destination_hash        VARCHAR(64) NOT NULL,
    reason                  VARCHAR(128),
    provider                VARCHAR(64),
    created_at              TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uq_notif_suppression_dest UNIQUE (tenant_id, channel, destination_hash),
    CONSTRAINT chk_notif_suppression_channel CHECK (channel IN ('EMAIL', 'WHATSAPP'))
);

CREATE INDEX IF NOT EXISTS idx_notif_suppression_lookup
    ON ar_notification_suppression (tenant_id, channel, destination_hash);

ALTER TABLE ar_notification_suppression ENABLE ROW LEVEL SECURITY;

DROP POLICY IF EXISTS tenant_isolation_notif_suppression ON ar_notification_suppression;
CREATE POLICY tenant_isolation_notif_suppression ON ar_notification_suppression
    USING (tenant_id::text = current_setting('app.current_tenant_id', true));
