-- STORY-020: Seed system chart of accounts for demo tenant + document enum bridge
-- Domain Account enum codes (AR, BANK, CASH, REVENUE, REVENUE_DISCOUNT, AP, EXPENSE)
-- are mirrored as system rows. Ledger posts still use account VARCHAR; account_id filled when present.

INSERT INTO ar_account (id, tenant_id, code, name, type, category, is_system, is_active, created_at, updated_at)
SELECT gen_random_uuid(),
       '00000000-0000-0000-0000-000000000001'::uuid,
       v.code,
       v.name,
       v.type,
       'SYSTEM',
       true,
       true,
       now(),
       now()
FROM (VALUES
    ('AR', 'Accounts Receivable', 'ASSET'),
    ('BANK', 'Bank', 'ASSET'),
    ('CASH', 'Cash', 'ASSET'),
    ('REVENUE', 'Revenue', 'REVENUE'),
    ('REVENUE_DISCOUNT', 'Discount Revenue', 'REVENUE'),
    ('AP', 'Accounts Payable', 'LIABILITY'),
    ('EXPENSE', 'Operating Expenses', 'EXPENSE')
) AS v(code, name, type)
WHERE EXISTS (SELECT 1 FROM ar_tenant t WHERE t.id = '00000000-0000-0000-0000-000000000001')
  AND NOT EXISTS (
      SELECT 1 FROM ar_account a
      WHERE a.tenant_id = '00000000-0000-0000-0000-000000000001'
        AND a.code = v.code
  );

-- Period close / open AR posting periods: deferred (full GL product). See PRODUCT_OWNER_STORIES STORY-020.
