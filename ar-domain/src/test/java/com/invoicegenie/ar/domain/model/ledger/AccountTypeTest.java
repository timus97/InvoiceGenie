package com.invoicegenie.ar.domain.model.ledger;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("AccountType Enum")
class AccountTypeTest {

    @Test
    @DisplayName("should have all expected types")
    void shouldHaveAllExpectedTypes() {
        AccountType[] types = AccountType.values();
        
        assertEquals(5, types.length);
        assertNotNull(AccountType.ASSET);
        assertNotNull(AccountType.LIABILITY);
        assertNotNull(AccountType.EQUITY);
        assertNotNull(AccountType.REVENUE);
        assertNotNull(AccountType.EXPENSE);
    }

    @Test
    @DisplayName("should have correct names")
    void shouldHaveCorrectNames() {
        assertEquals("ASSET", AccountType.ASSET.name());
        assertEquals("LIABILITY", AccountType.LIABILITY.name());
        assertEquals("EQUITY", AccountType.EQUITY.name());
        assertEquals("REVENUE", AccountType.REVENUE.name());
        assertEquals("EXPENSE", AccountType.EXPENSE.name());
    }

    @Test
    @DisplayName("should be able to get by name")
    void shouldBeAbleToGetByName() {
        assertEquals(AccountType.ASSET, AccountType.valueOf("ASSET"));
        assertEquals(AccountType.LIABILITY, AccountType.valueOf("LIABILITY"));
        assertEquals(AccountType.EQUITY, AccountType.valueOf("EQUITY"));
        assertEquals(AccountType.REVENUE, AccountType.valueOf("REVENUE"));
        assertEquals(AccountType.EXPENSE, AccountType.valueOf("EXPENSE"));
    }
}
