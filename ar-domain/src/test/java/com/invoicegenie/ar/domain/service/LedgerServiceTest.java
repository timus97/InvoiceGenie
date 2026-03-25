package com.invoicegenie.ar.domain.service;

import com.invoicegenie.ar.domain.model.ledger.EntryType;
import com.invoicegenie.ar.domain.model.ledger.LedgerEntry;
import com.invoicegenie.shared.domain.Money;
import com.invoicegenie.shared.domain.TenantId;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("LedgerService")
class LedgerServiceTest {

    private LedgerService service;
    private TenantId tenantId;

    @BeforeEach
    void setUp() {
        service = new LedgerService();
        tenantId = TenantId.of(UUID.randomUUID());
    }

    @Nested
    @DisplayName("Record Invoice Issued")
    class RecordInvoiceIssued {

        @Test
        @DisplayName("should create balanced entries for invoice")
        void shouldCreateBalancedEntriesForInvoice() {
            Money amount = Money.of("1000.00", "USD");
            UUID invoiceId = UUID.randomUUID();
            
            LedgerService.TransactionResult result = service.recordInvoiceIssued(
                    tenantId, invoiceId, "INV-001", amount);
            
            assertNotNull(result.transactionId());
            assertEquals(2, result.entries().size());
            assertTrue(result.balanced());
        }

        @Test
        @DisplayName("should create debit to AR and credit to Revenue")
        void shouldCreateDebitToARAndCreditToRevenue() {
            Money amount = Money.of("1000.00", "USD");
            
            LedgerService.TransactionResult result = service.recordInvoiceIssued(
                    tenantId, UUID.randomUUID(), "INV-001", amount);
            
            List<LedgerEntry> entries = result.entries();
            
            // Find AR entry (should be debit)
            LedgerEntry arEntry = entries.stream()
                    .filter(e -> e.getAccount().name().equals("AR"))
                    .findFirst().orElseThrow();
            assertEquals(EntryType.DEBIT, arEntry.getEntryType());
            
            // Find Revenue entry (should be credit)
            LedgerEntry revenueEntry = entries.stream()
                    .filter(e -> e.getAccount().name().equals("REVENUE"))
                    .findFirst().orElseThrow();
            assertEquals(EntryType.CREDIT, revenueEntry.getEntryType());
        }

        @Test
        @DisplayName("should calculate total debits and credits")
        void shouldCalculateTotalDebitsAndCredits() {
            Money amount = Money.of("1000.00", "USD");
            
            LedgerService.TransactionResult result = service.recordInvoiceIssued(
                    tenantId, UUID.randomUUID(), "INV-001", amount);
            
            Money debits = result.getTotalDebits();
            Money credits = result.getTotalCredits();
            
            assertEquals(0, amount.getAmount().compareTo(debits.getAmount()));
            assertEquals(0, amount.getAmount().compareTo(credits.getAmount()));
        }
    }

    @Nested
    @DisplayName("Record Payment Received")
    class RecordPaymentReceived {

        @Test
        @DisplayName("should create balanced entries for payment")
        void shouldCreateBalancedEntriesForPayment() {
            Money amount = Money.of("500.00", "USD");
            
            LedgerService.TransactionResult result = service.recordPaymentReceived(
                    tenantId, UUID.randomUUID(), "PAY-001", amount);
            
            assertEquals(2, result.entries().size());
            assertTrue(result.balanced());
        }

        @Test
        @DisplayName("should create debit to Bank and credit to AR")
        void shouldCreateDebitToBankAndCreditToAR() {
            Money amount = Money.of("500.00", "USD");
            
            LedgerService.TransactionResult result = service.recordPaymentReceived(
                    tenantId, UUID.randomUUID(), "PAY-001", amount);
            
            List<LedgerEntry> entries = result.entries();
            
            // Find Bank entry (should be debit)
            LedgerEntry bankEntry = entries.stream()
                    .filter(e -> e.getAccount().name().equals("BANK"))
                    .findFirst().orElseThrow();
            assertEquals(EntryType.DEBIT, bankEntry.getEntryType());
            
            // Find AR entry (should be credit)
            LedgerEntry arEntry = entries.stream()
                    .filter(e -> e.getAccount().name().equals("AR"))
                    .findFirst().orElseThrow();
            assertEquals(EntryType.CREDIT, arEntry.getEntryType());
        }
    }

    @Nested
    @DisplayName("Record Write Off")
    class RecordWriteOff {

        @Test
        @DisplayName("should create balanced entries for write-off")
        void shouldCreateBalancedEntriesForWriteOff() {
            Money amount = Money.of("200.00", "USD");
            
            LedgerService.TransactionResult result = service.recordWriteOff(
                    tenantId, UUID.randomUUID(), "INV-001", amount);
            
            assertEquals(2, result.entries().size());
            assertTrue(result.balanced());
        }

        @Test
        @DisplayName("should create debit to Expense and credit to AR")
        void shouldCreateDebitToExpenseAndCreditToAR() {
            Money amount = Money.of("200.00", "USD");
            
            LedgerService.TransactionResult result = service.recordWriteOff(
                    tenantId, UUID.randomUUID(), "INV-001", amount);
            
            List<LedgerEntry> entries = result.entries();
            
            // Find Expense entry (should be debit)
            LedgerEntry expenseEntry = entries.stream()
                    .filter(e -> e.getAccount().name().equals("EXPENSE"))
                    .findFirst().orElseThrow();
            assertEquals(EntryType.DEBIT, expenseEntry.getEntryType());
            
            // Find AR entry (should be credit)
            LedgerEntry arEntry = entries.stream()
                    .filter(e -> e.getAccount().name().equals("AR"))
                    .findFirst().orElseThrow();
            assertEquals(EntryType.CREDIT, arEntry.getEntryType());
        }
    }

    @Nested
    @DisplayName("Validate Balanced")
    class ValidateBalanced {

        @Test
        @DisplayName("should return true for balanced entries")
        void shouldReturnTrueForBalanced() {
            Money amount = Money.of("100.00", "USD");
            UUID txnId = UUID.randomUUID();
            
            List<LedgerEntry> entries = List.of(
                    new LedgerEntry(
                            com.invoicegenie.ar.domain.model.ledger.Account.AR,
                            amount, EntryType.DEBIT, "Test", txnId, "TEST", UUID.randomUUID()),
                    new LedgerEntry(
                            com.invoicegenie.ar.domain.model.ledger.Account.REVENUE,
                            amount, EntryType.CREDIT, "Test", txnId, "TEST", UUID.randomUUID())
            );
            
            assertTrue(service.validateBalanced(entries));
        }

        @Test
        @DisplayName("should return false for unbalanced entries")
        void shouldReturnFalseForUnbalanced() {
            Money amount = Money.of("100.00", "USD");
            UUID txnId = UUID.randomUUID();
            
            List<LedgerEntry> entries = List.of(
                    new LedgerEntry(
                            com.invoicegenie.ar.domain.model.ledger.Account.AR,
                            amount, EntryType.DEBIT, "Test", txnId, "TEST", UUID.randomUUID()),
                    new LedgerEntry(
                            com.invoicegenie.ar.domain.model.ledger.Account.REVENUE,
                            Money.of("50.00", "USD"), EntryType.CREDIT, "Test", txnId, "TEST", UUID.randomUUID())
            );
            
            assertFalse(service.validateBalanced(entries));
        }

        @Test
        @DisplayName("should return false for empty list")
        void shouldReturnFalseForEmptyList() {
            assertFalse(service.validateBalanced(List.of()));
        }

        @Test
        @DisplayName("should return false for null list")
        void shouldReturnFalseForNullList() {
            assertFalse(service.validateBalanced(null));
        }
    }

    @Nested
    @DisplayName("Assert Balanced")
    class AssertBalanced {

        @Test
        @DisplayName("should not throw for balanced entries")
        void shouldNotThrowForBalanced() {
            Money amount = Money.of("100.00", "USD");
            UUID txnId = UUID.randomUUID();
            
            List<LedgerEntry> entries = List.of(
                    new LedgerEntry(
                            com.invoicegenie.ar.domain.model.ledger.Account.AR,
                            amount, EntryType.DEBIT, "Test", txnId, "TEST", UUID.randomUUID()),
                    new LedgerEntry(
                            com.invoicegenie.ar.domain.model.ledger.Account.REVENUE,
                            amount, EntryType.CREDIT, "Test", txnId, "TEST", UUID.randomUUID())
            );
            
            assertDoesNotThrow(() -> service.assertBalanced(entries));
        }

        @Test
        @DisplayName("should throw for unbalanced entries")
        void shouldThrowForUnbalanced() {
            Money amount = Money.of("100.00", "USD");
            UUID txnId = UUID.randomUUID();
            
            List<LedgerEntry> entries = List.of(
                    new LedgerEntry(
                            com.invoicegenie.ar.domain.model.ledger.Account.AR,
                            amount, EntryType.DEBIT, "Test", txnId, "TEST", UUID.randomUUID())
            );
            
            assertThrows(IllegalStateException.class, () -> service.assertBalanced(entries));
        }
    }
}
