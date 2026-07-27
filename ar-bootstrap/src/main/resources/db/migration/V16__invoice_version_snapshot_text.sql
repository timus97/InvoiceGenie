-- V16: invoice version snapshot mapped as String in JPA (varchar bind) — align column type
ALTER TABLE ar_invoice_version
    ALTER COLUMN snapshot TYPE TEXT
    USING CASE
        WHEN snapshot IS NULL THEN NULL
        ELSE snapshot::text
    END;
