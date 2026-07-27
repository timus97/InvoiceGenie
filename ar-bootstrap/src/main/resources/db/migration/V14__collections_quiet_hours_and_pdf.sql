-- ============================================================================
-- V14: Collections production path (PP-010 quiet hours, PP-012 attach PDF,
--       PP-013 STATEMENT_SEND event type)
-- Note: V13 reserved for messaging agent (provider suppressions).
-- ============================================================================

-- Quiet hours + attach PDF on issue (tenant notification policy)
ALTER TABLE ar_notification_policy
    ADD COLUMN IF NOT EXISTS quiet_hours_start INT,
    ADD COLUMN IF NOT EXISTS quiet_hours_end INT,
    ADD COLUMN IF NOT EXISTS timezone VARCHAR(64) NOT NULL DEFAULT 'UTC',
    ADD COLUMN IF NOT EXISTS attach_pdf_on_issue BOOLEAN NOT NULL DEFAULT false;

ALTER TABLE ar_notification_policy
    DROP CONSTRAINT IF EXISTS chk_quiet_hours_start;
ALTER TABLE ar_notification_policy
    ADD CONSTRAINT chk_quiet_hours_start CHECK (
        quiet_hours_start IS NULL OR (quiet_hours_start >= 0 AND quiet_hours_start <= 1439)
    );

ALTER TABLE ar_notification_policy
    DROP CONSTRAINT IF EXISTS chk_quiet_hours_end;
ALTER TABLE ar_notification_policy
    ADD CONSTRAINT chk_quiet_hours_end CHECK (
        quiet_hours_end IS NULL OR (quiet_hours_end >= 0 AND quiet_hours_end <= 1439)
    );

-- Allow STATEMENT_SEND event type for customer statement emails
ALTER TABLE ar_notification DROP CONSTRAINT IF EXISTS chk_notif_event;
ALTER TABLE ar_notification
    ADD CONSTRAINT chk_notif_event CHECK (event_type IN (
        'INVOICE_ISSUED', 'PAYMENT_REMINDER', 'DUNNING_NOTICE', 'STATEMENT_SEND'
    ));

ALTER TABLE ar_notification_template DROP CONSTRAINT IF EXISTS chk_notif_template_event;
ALTER TABLE ar_notification_template
    ADD CONSTRAINT chk_notif_template_event CHECK (event_type IN (
        'INVOICE_ISSUED', 'PAYMENT_REMINDER', 'DUNNING_NOTICE', 'STATEMENT_SEND'
    ));

-- Seed system EMAIL template for statement send
INSERT INTO ar_notification_template (id, tenant_id, event_type, channel, locale, subject, body, active)
VALUES (
    'a1000000-0000-0000-0000-000000000004',
    NULL,
    'STATEMENT_SEND',
    'EMAIL',
    'en',
    'Account statement as of {{asOfDate}}',
    E'Hello {{customerName}},\n\nPlease find your account statement as of {{asOfDate}}.\nOpen balance: {{totalBalance}} {{currency}} ({{openItemCount}} open item(s)).\n\n{{statementSummary}}\n\nThank you,\nInvoiceGenie',
    true
)
ON CONFLICT (id) DO NOTHING;
