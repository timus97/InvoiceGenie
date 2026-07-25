-- ============================================================================
-- V12: Notification QA fixes — WhatsApp template seeds + documentation
-- ============================================================================

-- Seed system WhatsApp templates (logging provider uses body text; Meta maps wa template name)
INSERT INTO ar_notification_template (id, tenant_id, event_type, channel, locale, subject, body, whatsapp_template_name, active)
VALUES
(
    'b1000000-0000-0000-0000-000000000001',
    NULL,
    'INVOICE_ISSUED',
    'WHATSAPP',
    'en',
    NULL,
    'Invoice {{invoiceNumber}} for {{balanceDue}} {{currency}} is due {{dueDate}}. — {{tenantName}}',
    'invoice_issued_en',
    true
),
(
    'b1000000-0000-0000-0000-000000000002',
    NULL,
    'PAYMENT_REMINDER',
    'WHATSAPP',
    'en',
    NULL,
    'Reminder: invoice {{invoiceNumber}} balance {{balanceDue}} {{currency}} is due {{dueDate}}.',
    'payment_reminder_en',
    true
),
(
    'b1000000-0000-0000-0000-000000000003',
    NULL,
    'DUNNING_NOTICE',
    'WHATSAPP',
    'en',
    NULL,
    'Overdue notice: invoice {{invoiceNumber}} balance {{balanceDue}} {{currency}} was due {{dueDate}}.',
    'dunning_notice_en',
    true
)
ON CONFLICT (id) DO NOTHING;