-- ============================================================================
-- V4: JSONB columns that JPA maps as String/TEXT (Hibernate binds varchar)
-- ============================================================================

-- Customer.billing_address is a String in CustomerEntity
ALTER TABLE ar_customer
    ALTER COLUMN billing_address TYPE TEXT
    USING CASE
        WHEN billing_address IS NULL THEN NULL
        ELSE billing_address::text
    END;

-- Keep metadata as JSONB with default; unmapped by most entities (server default applies).
-- If any adapter later maps metadata as String, convert similarly.