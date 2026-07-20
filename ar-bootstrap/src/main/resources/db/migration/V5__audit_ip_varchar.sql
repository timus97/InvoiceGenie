-- ============================================================================
-- V5: Audit log ip_address - JPA maps String/varchar; V1 used INET
-- ============================================================================

ALTER TABLE ar_audit_log
    ALTER COLUMN ip_address TYPE VARCHAR(64)
    USING CASE
        WHEN ip_address IS NULL THEN NULL
        ELSE host(ip_address)
    END;