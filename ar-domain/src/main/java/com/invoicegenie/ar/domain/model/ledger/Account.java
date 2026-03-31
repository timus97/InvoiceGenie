package com.invoicegenie.ar.domain.model.ledger;

/**
 * Chart of Accounts for double-entry accounting.
 * 
 * <p>Core accounts for AR module:
 * <ul>
 *   <li>AR (Accounts Receivable): Asset - money owed by customers</li>
 *   <li>REVENUE: Income - money earned from sales</li>
 *   <li>BANK: Asset - money in bank accounts</li>
 * </ul>
 * 
 * <p>Account types:
 * <ul>
 *   <li>ASSET: Resources owned (AR, Bank, Cash)</li>
 *   <li>LIABILITY: Obligations owed (future: AP)</li>
 *   <li>EQUITY: Owner's stake</li>
 *   <li>REVENUE: Income from operations</li>
 *   <li>EXPENSE: Costs of operations (future)</li>
 * </ul>
 */
public enum Account {
    // Asset Accounts
    AR("Accounts Receivable", AccountType.ASSET),
    BANK("Bank", AccountType.ASSET),
    CASH("Cash", AccountType.ASSET),
    
    // Revenue Accounts
    REVENUE("Revenue", AccountType.REVENUE),
    REVENUE_DISCOUNT("Discount Revenue", AccountType.REVENUE),
    
    // Liability Accounts (for future AP)
    AP("Accounts Payable", AccountType.LIABILITY),
    
    // Expense Accounts (for future)
    EXPENSE("Operating Expenses", AccountType.EXPENSE);

    private final String displayName;
    private final AccountType type;

    Account(String displayName, AccountType type) {
        this.displayName = displayName;
        this.type = type;
    }

    public String getDisplayName() {
        return displayName;
    }

    public AccountType getType() {
        return type;
    }

    /**
     * Check if this is a debit-normal account (assets, expenses).
     * Debit increases balance, Credit decreases balance.
     */
    public boolean isDebitNormal() {
        return type == AccountType.ASSET || type == AccountType.EXPENSE;
    }

    /**
     * Check if this is a credit-normal account (liabilities, equity, revenue).
     * Credit increases balance, Debit decreases balance.
     */
    public boolean isCreditNormal() {
        return type == AccountType.LIABILITY || type == AccountType.EQUITY || type == AccountType.REVENUE;
    }
}
