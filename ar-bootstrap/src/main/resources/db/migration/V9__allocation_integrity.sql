-- STORY-019: guard rails for invoice outstanding and allocation amounts
-- amount_due is outstanding (total - paid); must stay non-negative

ALTER TABLE ar_invoice
    DROP CONSTRAINT IF EXISTS chk_invoice_amount_due_nonneg;

ALTER TABLE ar_invoice
    ADD CONSTRAINT chk_invoice_amount_due_nonneg CHECK (amount_due >= 0);

ALTER TABLE ar_payment_allocation
    DROP CONSTRAINT IF EXISTS chk_allocation_positive;

ALTER TABLE ar_payment_allocation
    ADD CONSTRAINT chk_allocation_positive CHECK (amount > 0);