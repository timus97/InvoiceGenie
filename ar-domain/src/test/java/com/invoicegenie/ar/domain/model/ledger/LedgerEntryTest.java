package com.invoicegenie.ar.domain.model.ledger;

import com.invoicegenie.shared.domain.Money;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("LedgerEntry")
class LedgerEntryTest {

    @Nested
    @DisplayName("Construction")
    class Construction {

        @Test
        @DisplayName("should create ledger entry with required fields")
        void shouldCreateWithRequiredFields() {
            Money amount = Money.of("1000.00", "USD");
            UUID transactionId = UUID.randomUUID();
            UUID referenceId = UUID.randomUUID();
            
            LedgerEntry entry = new LedgerEntry(
                    Account.AR, amount, EntryType.DEBIT, "Test entry",
                    transactionId, "INVOICE", referenceId);
            
            assertNotNull(entry.getId());
            assertEquals(Account.AR, entry.getAccount());
            assertEquals(amount, entry.getAmount());
            assertEquals(EntryType.DEBIT, entry.getEntryType());
            assertEquals("Test entry", entry.getDescription());
            assertEquals(transactionId, entry.getTransactionId());
            assertEquals("INVOICE", entry.getReferenceType());
            assertEquals(referenceId, entry.getReferenceId());
            assertNotNull(entry.getCreatedAt());
        }

        @Test
        @DisplayName("should create ledger entry with all fields")
        void shouldCreateWithAllFields() {
            UUID id = UUID.randomUUID();
            Money amount = Money.of("1000.00", "USD");
            UUID transactionId = UUID.randomUUID();
            UUID referenceId = UUID.randomUUID();
            Instant createdAt = Instant.now();
            
            LedgerEntry entry = new LedgerEntry(
                    id, Account.BANK, amount, EntryType.CREDIT, "Full entry",
                    transactionId, "PAYMENT", referenceId, createdAt);
            
            assertEquals(id, entry.getId());
            assertEquals(Account.BANK, entry.getAccount());
            assertEquals(createdAt, entry.getCreatedAt());
        }

        @Test
        @DisplayName("should throw when amount is not positive")
        void shouldThrowWhenAmountNotPositive() {
            assertThrows(IllegalArgumentException.class, () ->
                    new LedgerEntry(Account.AR, Money.of("0.00", "USD"), EntryType.DEBIT,
                            "Test", UUID.randomUUID(), "TEST", UUID.randomUUID()));
        }

        @Test
        @DisplayName("should throw when account is null")
        void shouldThrowWhenAccountNull() {
            assertThrows(NullPointerException.class, () ->
                    new LedgerEntry(null, Money.of("100.00", "USD"), EntryType.DEBIT,
                            "Test", UUID.randomUUID(), "TEST", UUID.randomUUID()));
        }

        @Test
        @DisplayName("should throw when entry type is null")
        void shouldThrowWhenEntryTypeNull() {
            assertThrows(NullPointerException.class, () ->
                    new LedgerEntry(Account.AR, Money.of("100.00", "USD"), null,
                            "Test", UUID.randomUUID(), "TEST", UUID.randomUUID()));
        }
    }

    @Nested
    @DisplayName("Signed Amount")
    class SignedAmount {

        @Test
        @DisplayName("should return positive for debit")
        void shouldReturnPositiveForDebit() {
            Money amount = Money.of("100.00", "USD");
            LedgerEntry entry = new LedgerEntry(
                    Account.AR, amount, EntryType.DEBIT, "Test",
                    UUID.randomUUID(), "TEST", UUID.randomUUID());
            
            Money signed = entry.getSignedAmount();
            
            assertEquals(0, amount.getAmount().compareTo(signed.getAmount()));
        }

        @Test
        @DisplayName("should return negative for credit")
        void shouldReturnNegativeForCredit() {
            Money amount = Money.of("100.00", "USD");
            LedgerEntry entry = new LedgerEntry(
                    Account.REVENUE, amount, EntryType.CREDIT, "Test",
                    UUID.randomUUID(), "TEST", UUID.randomUUID());
            
            Money signed = entry.getSignedAmount();
            
            assertTrue(signed.getAmount().signum() < 0);
        }
    }

    @Nested
    @DisplayName("Equality")
    class Equality {

        @Test
        @DisplayName("should be equal by id")
        void shouldBeEqualById() {
            UUID id = UUID.randomUUID();
            Instant now = Instant.now();
            
            LedgerEntry e1 = new LedgerEntry(id, Account.AR, Money.of("100.00", "USD"),
                    EntryType.DEBIT, "Test 1", UUID.randomUUID(), "TEST", UUID.randomUUID(), now);
            LedgerEntry e2 = new LedgerEntry(id, Account.BANK, Money.of("200.00", "EUR"),
                    EntryType.CREDIT, "Test 2", UUID.randomUUID(), "OTHER", UUID.randomUUID(), now);
            
            assertEquals(e1, e2);
        }

        @Test
        @DisplayName("should not be equal when different ids")
        void shouldNotBeEqualWhenDifferentIds() {
            LedgerEntry e1 = new LedgerEntry(Account.AR, Money.of("100.00", "USD"),
                    EntryType.DEBIT, "Test", UUID.randomUUID(), "TEST", UUID.randomUUID());
            LedgerEntry e2 = new LedgerEntry(Account.AR, Money.of("100.00", "USD"),
                    EntryType.DEBIT, "Test", UUID.randomUUID(), "TEST", UUID.randomUUID());
            
            assertNotEquals(e1, e2);
        }
    }

    @Nested
    @DisplayName("ToString")
    class ToStringTest {

        @Test
        @DisplayName("should contain key fields")
        void shouldContainKeyFields() {
            LedgerEntry entry = new LedgerEntry(
                    Account.AR, Money.of("100.00", "USD"), EntryType.DEBIT, "Test entry",
                    UUID.randomUUID(), "INVOICE", UUID.randomUUID());
            
            String str = entry.toString();
            
            assertTrue(str.contains("DEBIT"));
            assertTrue(str.contains("AR"));
            assertTrue(str.contains("Test entry"));
        }
    }
}
