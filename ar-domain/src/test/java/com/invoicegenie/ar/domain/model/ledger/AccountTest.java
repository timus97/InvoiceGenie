package com.invoicegenie.ar.domain.model.ledger;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("Account Enum")
class AccountTest {

    @Nested
    @DisplayName("Enum Values")
    class EnumValues {

        @Test
        @DisplayName("should have all expected accounts")
        void shouldHaveAllExpectedAccounts() {
            Account[] accounts = Account.values();
            
            assertEquals(7, accounts.length);
            assertNotNull(Account.AR);
            assertNotNull(Account.BANK);
            assertNotNull(Account.CASH);
            assertNotNull(Account.REVENUE);
            assertNotNull(Account.REVENUE_DISCOUNT);
            assertNotNull(Account.AP);
            assertNotNull(Account.EXPENSE);
        }

        @Test
        @DisplayName("should have correct names")
        void shouldHaveCorrectNames() {
            assertEquals("AR", Account.AR.name());
            assertEquals("BANK", Account.BANK.name());
            assertEquals("CASH", Account.CASH.name());
            assertEquals("REVENUE", Account.REVENUE.name());
            assertEquals("REVENUE_DISCOUNT", Account.REVENUE_DISCOUNT.name());
            assertEquals("AP", Account.AP.name());
            assertEquals("EXPENSE", Account.EXPENSE.name());
        }

        @Test
        @DisplayName("should be able to get by name")
        void shouldBeAbleToGetByName() {
            assertEquals(Account.AR, Account.valueOf("AR"));
            assertEquals(Account.BANK, Account.valueOf("BANK"));
            assertEquals(Account.CASH, Account.valueOf("CASH"));
            assertEquals(Account.REVENUE, Account.valueOf("REVENUE"));
            assertEquals(Account.REVENUE_DISCOUNT, Account.valueOf("REVENUE_DISCOUNT"));
            assertEquals(Account.AP, Account.valueOf("AP"));
            assertEquals(Account.EXPENSE, Account.valueOf("EXPENSE"));
        }
    }

    @Nested
    @DisplayName("Display Names")
    class DisplayNames {

        @Test
        @DisplayName("should have correct display names")
        void shouldHaveCorrectDisplayNames() {
            assertEquals("Accounts Receivable", Account.AR.getDisplayName());
            assertEquals("Bank", Account.BANK.getDisplayName());
            assertEquals("Cash", Account.CASH.getDisplayName());
            assertEquals("Revenue", Account.REVENUE.getDisplayName());
            assertEquals("Discount Revenue", Account.REVENUE_DISCOUNT.getDisplayName());
            assertEquals("Accounts Payable", Account.AP.getDisplayName());
            assertEquals("Operating Expenses", Account.EXPENSE.getDisplayName());
        }
    }

    @Nested
    @DisplayName("Account Types")
    class AccountTypes {

        @Test
        @DisplayName("should have correct types")
        void shouldHaveCorrectTypes() {
            assertEquals(AccountType.ASSET, Account.AR.getType());
            assertEquals(AccountType.ASSET, Account.BANK.getType());
            assertEquals(AccountType.ASSET, Account.CASH.getType());
            assertEquals(AccountType.REVENUE, Account.REVENUE.getType());
            assertEquals(AccountType.REVENUE, Account.REVENUE_DISCOUNT.getType());
            assertEquals(AccountType.LIABILITY, Account.AP.getType());
            assertEquals(AccountType.EXPENSE, Account.EXPENSE.getType());
        }
    }

    @Nested
    @DisplayName("Debit/Credit Normal")
    class DebitCreditNormal {

        @Test
        @DisplayName("should identify debit normal accounts")
        void shouldIdentifyDebitNormalAccounts() {
            assertTrue(Account.AR.isDebitNormal());
            assertTrue(Account.BANK.isDebitNormal());
            assertTrue(Account.CASH.isDebitNormal());
            assertTrue(Account.EXPENSE.isDebitNormal());
        }

        @Test
        @DisplayName("should identify credit normal accounts")
        void shouldIdentifyCreditNormalAccounts() {
            assertTrue(Account.REVENUE.isCreditNormal());
            assertTrue(Account.REVENUE_DISCOUNT.isCreditNormal());
            assertTrue(Account.AP.isCreditNormal());
        }

        @Test
        @DisplayName("should not have accounts that are both debit and credit normal")
        void shouldNotHaveBothDebitAndCreditNormal() {
            for (Account account : Account.values()) {
                assertFalse(account.isDebitNormal() && account.isCreditNormal());
            }
        }
    }
}
