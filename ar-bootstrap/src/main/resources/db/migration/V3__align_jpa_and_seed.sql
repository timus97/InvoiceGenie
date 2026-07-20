-- ============================================================================
-- V3: Align Flyway schema with JPA entities + seed demo tenant
-- Source of truth after this migration: JPA entities under ar-adapter-persistence
-- ============================================================================

-- ---------------------------------------------------------------------------
-- Demo tenant (matches .env.example API key binding)
-- ---------------------------------------------------------------------------
INSERT INTO ar_tenant (id, code, name, base_currency, status, settings)
VALUES (
    '00000000-0000-0000-0000-000000000001',
    'DEMO',
    'Demo Tenant',
    'USD',
    'ACTIVE',
    '{}'::jsonb
)
ON CONFLICT (id) DO NOTHING;

-- ---------------------------------------------------------------------------
-- Invoice: currency column name used by InvoiceEntity.currency_code
-- ---------------------------------------------------------------------------
DO $$
BEGIN
    IF EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_name = 'ar_invoice' AND column_name = 'currency'
    ) AND NOT EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_name = 'ar_invoice' AND column_name = 'currency_code'
    ) THEN
        ALTER TABLE ar_invoice RENAME COLUMN currency TO currency_code;
    END IF;
END $$;

-- customer_ref is required by JPA; keep empty string for any legacy nulls
UPDATE ar_invoice SET customer_ref = COALESCE(customer_ref, '') WHERE customer_ref IS NULL;
ALTER TABLE ar_invoice ALTER COLUMN customer_ref SET DEFAULT '';
ALTER TABLE ar_invoice ALTER COLUMN customer_ref SET NOT NULL;

-- ---------------------------------------------------------------------------
-- Invoice line: JPA uses composite key (tenant_id, invoice_id, sequence).
-- V1 declared a non-null id without default that breaks Hibernate inserts.
-- ---------------------------------------------------------------------------
DO $$
BEGIN
    IF EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_name = 'ar_invoice_line' AND column_name = 'id'
    ) THEN
        ALTER TABLE ar_invoice_line ALTER COLUMN id DROP NOT NULL;
        ALTER TABLE ar_invoice_line ALTER COLUMN id SET DEFAULT gen_random_uuid();
    END IF;
END $$;

-- ---------------------------------------------------------------------------
-- Payment: optimistic lock column used by PaymentEntity.version
-- ---------------------------------------------------------------------------
ALTER TABLE ar_payment
    ADD COLUMN IF NOT EXISTS version BIGINT NOT NULL DEFAULT 1;

-- ---------------------------------------------------------------------------
-- Ledger: domain Account enum stored as VARCHAR; group legs by transaction_id
-- Keep account_id nullable for future COA; drop hard NOT NULL / FK for P1
-- ---------------------------------------------------------------------------
ALTER TABLE ar_ledger_entry
    ADD COLUMN IF NOT EXISTS account VARCHAR(64);

ALTER TABLE ar_ledger_entry
    ADD COLUMN IF NOT EXISTS transaction_id UUID;

ALTER TABLE ar_ledger_entry
    ADD COLUMN IF NOT EXISTS reference_type VARCHAR(64);

ALTER TABLE ar_ledger_entry
    ADD COLUMN IF NOT EXISTS reference_id UUID;

-- Drop composite FK to ar_account if present (name from V1)
ALTER TABLE ar_ledger_entry
    DROP CONSTRAINT IF EXISTS ar_ledger_entry_tenant_id_account_id_fkey;

ALTER TABLE ar_ledger_entry
    ALTER COLUMN account_id DROP NOT NULL;

-- Empty DB: enforce NOT NULL on new required columns for new rows
UPDATE ar_ledger_entry
SET account = COALESCE(account, 'UNKNOWN')
WHERE account IS NULL;

UPDATE ar_ledger_entry
SET transaction_id = COALESCE(transaction_id, id)
WHERE transaction_id IS NULL;

ALTER TABLE ar_ledger_entry
    ALTER COLUMN account SET NOT NULL;

ALTER TABLE ar_ledger_entry
    ALTER COLUMN transaction_id SET NOT NULL;

CREATE INDEX IF NOT EXISTS idx_ledger_tenant_account_name_date
    ON ar_ledger_entry (tenant_id, account, posting_date);

CREATE INDEX IF NOT EXISTS idx_ledger_tenant_transaction
    ON ar_ledger_entry (tenant_id, transaction_id);

-- ---------------------------------------------------------------------------
-- Outbox: JPA maps payload as TEXT; accept text payloads without cast issues
-- ---------------------------------------------------------------------------
DO $$
BEGIN
    IF EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_name = 'ar_outbox'
          AND column_name = 'payload'
          AND data_type = 'jsonb'
    ) THEN
        ALTER TABLE ar_outbox
            ALTER COLUMN payload TYPE TEXT USING payload::text;
    END IF;
END $$;

-- ---------------------------------------------------------------------------
-- Audit log: JPA uses TEXT for before/after states
-- ---------------------------------------------------------------------------
DO $$
BEGIN
    IF EXISTS (
        SELECT 1 FROM information_schema.columns
        WHERE table_name = 'ar_audit_log'
          AND column_name = 'before_state'
          AND data_type = 'jsonb'
    ) THEN
        ALTER TABLE ar_audit_log
            ALTER COLUMN before_state TYPE TEXT USING before_state::text;
        ALTER TABLE ar_audit_log
            ALTER COLUMN after_state TYPE TEXT USING after_state::text;
    END IF;
END $$;