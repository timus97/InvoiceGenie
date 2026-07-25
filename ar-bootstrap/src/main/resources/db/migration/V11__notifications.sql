-- ============================================================================
-- V11: Customer notifications (Email + WhatsApp) — P0 MVP
-- Tables: template, preference, policy, notification, attempt
--
-- RLS notes:
-- * Tenant-scoped tables use standard isolation via app.current_tenant_id GUC.
-- * NotificationDispatchWorker.findDue / cross-tenant workers typically run as
--   table owner (ar) which bypasses RLS unless FORCE ROW LEVEL SECURITY is set.
-- * If FORCE RLS is enabled later, add a worker role with BYPASSRLS or a
--   policy allowing empty GUC for status IN ('PENDING','QUEUED','SENDING')
--   with next_attempt_at due — document here before enabling FORCE.
-- ============================================================================

-- ---------------------------------------------------------------------------
-- Templates (system + tenant overrides)
-- tenant_id NULL = system default template
-- ---------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS ar_notification_template (
    id              UUID PRIMARY KEY,
    tenant_id       UUID REFERENCES ar_tenant(id),
    event_type      VARCHAR(64) NOT NULL,
    channel         VARCHAR(32) NOT NULL,
    locale          VARCHAR(16) NOT NULL DEFAULT 'en',
    subject         VARCHAR(512),
    body            TEXT NOT NULL,
    whatsapp_template_name VARCHAR(128),
    active          BOOLEAN NOT NULL DEFAULT true,
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT chk_notif_template_event CHECK (event_type IN (
        'INVOICE_ISSUED', 'PAYMENT_REMINDER', 'DUNNING_NOTICE'
    )),
    CONSTRAINT chk_notif_template_channel CHECK (channel IN ('EMAIL', 'WHATSAPP'))
);

-- Unique active template per tenant/event/channel/locale (NULL tenant = system)
CREATE UNIQUE INDEX IF NOT EXISTS uq_notif_template_scope
    ON ar_notification_template (COALESCE(tenant_id, '00000000-0000-0000-0000-000000000000'), event_type, channel, locale)
    WHERE active = true;

CREATE INDEX IF NOT EXISTS idx_notif_template_lookup
    ON ar_notification_template (tenant_id, event_type, channel, locale);

-- RLS: system templates (tenant_id IS NULL) visible to all; tenant rows isolated
ALTER TABLE ar_notification_template ENABLE ROW LEVEL SECURITY;

DROP POLICY IF EXISTS tenant_isolation_notif_template ON ar_notification_template;
CREATE POLICY tenant_isolation_notif_template ON ar_notification_template
    USING (
        tenant_id IS NULL
        OR tenant_id::text = current_setting('app.current_tenant_id', true)
    );

-- ---------------------------------------------------------------------------
-- Customer channel preferences / consent
-- ---------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS ar_notification_preference (
    id              UUID PRIMARY KEY,
    tenant_id       UUID NOT NULL REFERENCES ar_tenant(id),
    customer_id     UUID NOT NULL,
    channel         VARCHAR(32) NOT NULL,
    enabled         BOOLEAN NOT NULL DEFAULT true,
    opted_out_at    TIMESTAMPTZ,
    destination_override VARCHAR(320),
    created_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at      TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uq_notif_pref_customer_channel UNIQUE (tenant_id, customer_id, channel),
    CONSTRAINT chk_notif_pref_channel CHECK (channel IN ('EMAIL', 'WHATSAPP'))
);

CREATE INDEX IF NOT EXISTS idx_notif_pref_tenant_customer
    ON ar_notification_preference (tenant_id, customer_id);

ALTER TABLE ar_notification_preference ENABLE ROW LEVEL SECURITY;

DROP POLICY IF EXISTS tenant_isolation_notif_pref ON ar_notification_preference;
CREATE POLICY tenant_isolation_notif_pref ON ar_notification_preference
    USING (tenant_id::text = current_setting('app.current_tenant_id', true));

-- ---------------------------------------------------------------------------
-- Tenant notification policy
-- ---------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS ar_notification_policy (
    id                      UUID PRIMARY KEY,
    tenant_id               UUID NOT NULL REFERENCES ar_tenant(id),
    enabled                 BOOLEAN NOT NULL DEFAULT true,
    email_enabled           BOOLEAN NOT NULL DEFAULT true,
    whatsapp_enabled        BOOLEAN NOT NULL DEFAULT false,
    auto_send_on_issue      BOOLEAN NOT NULL DEFAULT true,
    pre_due_reminder_enabled BOOLEAN NOT NULL DEFAULT true,
    pre_due_days            INT NOT NULL DEFAULT 3,
    dunning_notice_enabled  BOOLEAN NOT NULL DEFAULT true,
    channels_invoice_issued VARCHAR(64) NOT NULL DEFAULT 'EMAIL',
    channels_payment_reminder VARCHAR(64) NOT NULL DEFAULT 'EMAIL',
    channels_dunning_notice VARCHAR(64) NOT NULL DEFAULT 'EMAIL',
    created_at              TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at              TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uq_notif_policy_tenant UNIQUE (tenant_id),
    CONSTRAINT chk_pre_due_days CHECK (pre_due_days >= 0 AND pre_due_days <= 90)
);

ALTER TABLE ar_notification_policy ENABLE ROW LEVEL SECURITY;

DROP POLICY IF EXISTS tenant_isolation_notif_policy ON ar_notification_policy;
CREATE POLICY tenant_isolation_notif_policy ON ar_notification_policy
    USING (tenant_id::text = current_setting('app.current_tenant_id', true));

-- ---------------------------------------------------------------------------
-- Notification queue / history
-- ---------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS ar_notification (
    id                  UUID PRIMARY KEY,
    tenant_id           UUID NOT NULL REFERENCES ar_tenant(id),
    customer_id         UUID,
    invoice_id          UUID,
    event_type          VARCHAR(64) NOT NULL,
    channel             VARCHAR(32) NOT NULL,
    status              VARCHAR(32) NOT NULL,
    idempotency_key     VARCHAR(256) NOT NULL,
    destination         VARCHAR(320),
    subject             VARCHAR(512),
    body                TEXT,
    template_id         UUID,
    skip_reason         VARCHAR(64),
    error_message       VARCHAR(2000),
    attempt_count       INT NOT NULL DEFAULT 0,
    max_attempts        INT NOT NULL DEFAULT 5,
    next_attempt_at     TIMESTAMPTZ,
    sent_at             TIMESTAMPTZ,
    provider_message_id VARCHAR(256),
    metadata_json       TEXT,
    created_at          TIMESTAMPTZ NOT NULL DEFAULT now(),
    updated_at          TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT uq_notif_idempotency UNIQUE (tenant_id, idempotency_key),
    CONSTRAINT chk_notif_event CHECK (event_type IN (
        'INVOICE_ISSUED', 'PAYMENT_REMINDER', 'DUNNING_NOTICE'
    )),
    CONSTRAINT chk_notif_channel CHECK (channel IN ('EMAIL', 'WHATSAPP')),
    CONSTRAINT chk_notif_status CHECK (status IN (
        'PENDING', 'QUEUED', 'SENDING', 'SENT', 'FAILED', 'CANCELLED', 'SKIPPED'
    ))
);

CREATE INDEX IF NOT EXISTS idx_notif_tenant_created
    ON ar_notification (tenant_id, created_at DESC);

CREATE INDEX IF NOT EXISTS idx_notif_tenant_invoice
    ON ar_notification (tenant_id, invoice_id);

CREATE INDEX IF NOT EXISTS idx_notif_tenant_customer
    ON ar_notification (tenant_id, customer_id);

-- Cross-tenant dispatch worker poll (owner bypasses RLS; see header notes)
CREATE INDEX IF NOT EXISTS idx_notif_due_dispatch
    ON ar_notification (status, next_attempt_at)
    WHERE status IN ('PENDING', 'QUEUED');

ALTER TABLE ar_notification ENABLE ROW LEVEL SECURITY;

DROP POLICY IF EXISTS tenant_isolation_notif ON ar_notification;
CREATE POLICY tenant_isolation_notif ON ar_notification
    USING (tenant_id::text = current_setting('app.current_tenant_id', true));

-- ---------------------------------------------------------------------------
-- Delivery attempts (audit)
-- ---------------------------------------------------------------------------
CREATE TABLE IF NOT EXISTS ar_notification_attempt (
    id                  UUID PRIMARY KEY,
    tenant_id           UUID NOT NULL REFERENCES ar_tenant(id),
    notification_id     UUID NOT NULL REFERENCES ar_notification(id) ON DELETE CASCADE,
    attempt_number      INT NOT NULL,
    status              VARCHAR(32) NOT NULL,
    provider            VARCHAR(64),
    provider_message_id VARCHAR(256),
    http_status         INT,
    error_message       VARCHAR(2000),
    response_snippet    VARCHAR(2000),
    attempted_at        TIMESTAMPTZ NOT NULL DEFAULT now(),
    CONSTRAINT chk_notif_attempt_status CHECK (status IN (
        'SUCCESS', 'FAILED', 'SKIPPED'
    ))
);

CREATE INDEX IF NOT EXISTS idx_notif_attempt_notification
    ON ar_notification_attempt (tenant_id, notification_id, attempt_number);

ALTER TABLE ar_notification_attempt ENABLE ROW LEVEL SECURITY;

DROP POLICY IF EXISTS tenant_isolation_notif_attempt ON ar_notification_attempt;
CREATE POLICY tenant_isolation_notif_attempt ON ar_notification_attempt
    USING (tenant_id::text = current_setting('app.current_tenant_id', true));

-- ---------------------------------------------------------------------------
-- Seed: system EMAIL templates
-- ---------------------------------------------------------------------------
INSERT INTO ar_notification_template (id, tenant_id, event_type, channel, locale, subject, body, active)
VALUES
(
    'a1000000-0000-0000-0000-000000000001',
    NULL,
    'INVOICE_ISSUED',
    'EMAIL',
    'en',
    'Invoice {{invoiceNumber}} from InvoiceGenie',
    E'Hello {{customerName}},\n\nYour invoice {{invoiceNumber}} for {{total}} {{currency}} has been issued.\nDue date: {{dueDate}}.\n\nThank you,\nInvoiceGenie',
    true
),
(
    'a1000000-0000-0000-0000-000000000002',
    NULL,
    'PAYMENT_REMINDER',
    'EMAIL',
    'en',
    'Payment reminder: invoice {{invoiceNumber}} due {{dueDate}}',
    E'Hello {{customerName}},\n\nThis is a friendly reminder that invoice {{invoiceNumber}} for {{balanceDue}} {{currency}} is due on {{dueDate}}.\n\nThank you,\nInvoiceGenie',
    true
),
(
    'a1000000-0000-0000-0000-000000000003',
    NULL,
    'DUNNING_NOTICE',
    'EMAIL',
    'en',
    'Overdue notice (level {{dunningLevel}}): invoice {{invoiceNumber}}',
    E'Hello {{customerName}},\n\nInvoice {{invoiceNumber}} is {{daysPastDue}} days past due. Outstanding balance: {{balanceDue}} {{currency}}.\nDunning level: {{dunningLevel}}.\n\nPlease arrange payment promptly.\n\nInvoiceGenie',
    true
)
ON CONFLICT (id) DO NOTHING;

-- ---------------------------------------------------------------------------
-- Seed: policy for demo tenant
-- ---------------------------------------------------------------------------
INSERT INTO ar_notification_policy (
    id, tenant_id, enabled, email_enabled, whatsapp_enabled,
    auto_send_on_issue, pre_due_reminder_enabled, pre_due_days, dunning_notice_enabled,
    channels_invoice_issued, channels_payment_reminder, channels_dunning_notice
)
VALUES (
    'b1000000-0000-0000-0000-000000000001',
    '00000000-0000-0000-0000-000000000001',
    true, true, false,
    true, true, 3, true,
    'EMAIL', 'EMAIL', 'EMAIL'
)
ON CONFLICT (tenant_id) DO NOTHING;
