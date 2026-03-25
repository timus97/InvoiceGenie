package com.invoicegenie.ar.domain.model.payment;

import com.invoicegenie.ar.domain.model.customer.CustomerId;
import com.invoicegenie.shared.domain.Money;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("Cheque Aggregate")
class ChequeTest {

    private UUID chequeId;
    private CustomerId customerId;
    private Money amount;
    private Cheque cheque;

    @BeforeEach
    void setUp() {
        chequeId = UUID.randomUUID();
        customerId = CustomerId.of(UUID.randomUUID());
        amount = Money.of("5000.00", "USD");
        cheque = new Cheque(chequeId, "CHQ-123456", customerId, amount, 
                "Bank of America", "Main Branch", LocalDate.now(), "Test cheque");
    }

    @Nested
    @DisplayName("Construction")
    class Construction {

        @Test
        @DisplayName("should create cheque with required fields")
        void shouldCreateWithRequiredFields() {
            assertNotNull(cheque.getId());
            assertEquals("CHQ-123456", cheque.getChequeNumber());
            assertEquals(customerId, cheque.getCustomerId());
            assertEquals(amount, cheque.getAmount());
            assertEquals("Bank of America", cheque.getBankName());
            assertEquals(ChequeStatus.RECEIVED, cheque.getStatus());
        }

        @Test
        @DisplayName("should throw when cheque number is blank")
        void shouldThrowWhenChequeNumberBlank() {
            assertThrows(IllegalArgumentException.class, () ->
                    new Cheque(chequeId, "", customerId, amount, "Bank", "Branch", LocalDate.now(), null));
        }

        @Test
        @DisplayName("should throw when amount is not positive")
        void shouldThrowWhenAmountNotPositive() {
            assertThrows(IllegalArgumentException.class, () ->
                    new Cheque(chequeId, "CHQ-001", customerId, Money.of("0.00", "USD"), 
                            "Bank", "Branch", LocalDate.now(), null));
        }

        @Test
        @DisplayName("should create cheque with all fields for reconstitution")
        void shouldCreateWithAllFields() {
            Instant now = Instant.now();
            Cheque fullCheque = new Cheque(
                    chequeId, "CHQ-999", customerId, amount,
                    "Bank", "Branch", LocalDate.now(),
                    LocalDate.now(), LocalDate.now(), null, null, null,
                    ChequeStatus.DEPOSITED, null, List.of(), "Notes",
                    now, now
            );
            
            assertEquals("CHQ-999", fullCheque.getChequeNumber());
            assertEquals(ChequeStatus.DEPOSITED, fullCheque.getStatus());
        }
    }

    @Nested
    @DisplayName("Lifecycle")
    class Lifecycle {

        @Test
        @DisplayName("should deposit cheque")
        void shouldDepositCheque() {
            cheque.deposit();
            assertEquals(ChequeStatus.DEPOSITED, cheque.getStatus());
            assertNotNull(cheque.getDepositedDate());
        }

        @Test
        @DisplayName("should clear cheque")
        void shouldClearCheque() {
            cheque.deposit();
            Cheque.ChequeClearedResult result = cheque.clear();
            
            assertEquals(ChequeStatus.CLEARED, cheque.getStatus());
            assertNotNull(cheque.getClearedDate());
            assertEquals(chequeId, result.chequeId());
        }

        @Test
        @DisplayName("should bounce cheque")
        void shouldBounceCheque() {
            cheque.deposit();
            Cheque.ChequeBouncedResult result = cheque.bounce("Insufficient funds");
            
            assertEquals(ChequeStatus.BOUNCED, cheque.getStatus());
            assertNotNull(cheque.getBouncedDate());
            assertEquals("Insufficient funds", cheque.getBounceReason());
            assertEquals("Insufficient funds", result.reason());
        }

        @Test
        @DisplayName("should throw when depositing non-received cheque")
        void shouldThrowWhenDepositingNonReceived() {
            cheque.deposit();
            assertThrows(IllegalStateException.class, () -> cheque.deposit());
        }

        @Test
        @DisplayName("should throw when clearing non-deposited cheque")
        void shouldThrowWhenClearingNonDeposited() {
            assertThrows(IllegalStateException.class, () -> cheque.clear());
        }

        @Test
        @DisplayName("should throw when bouncing non-deposited cheque")
        void shouldThrowWhenBouncingNonDeposited() {
            assertThrows(IllegalStateException.class, () -> cheque.bounce("Reason"));
        }

        @Test
        @DisplayName("should throw when bounce reason is blank")
        void shouldThrowWhenBounceReasonBlank() {
            cheque.deposit();
            assertThrows(IllegalArgumentException.class, () -> cheque.bounce(""));
        }
    }

    @Nested
    @DisplayName("Allocated Invoices")
    class AllocatedInvoices {

        @Test
        @DisplayName("should add allocated invoice")
        void shouldAddAllocatedInvoice() {
            UUID invoiceId = UUID.randomUUID();
            cheque.addAllocatedInvoice(invoiceId);
            
            assertEquals(1, cheque.getAllocatedInvoiceIds().size());
            assertTrue(cheque.getAllocatedInvoiceIds().contains(invoiceId));
        }

        @Test
        @DisplayName("should not duplicate allocated invoice")
        void shouldNotDuplicateAllocatedInvoice() {
            UUID invoiceId = UUID.randomUUID();
            cheque.addAllocatedInvoice(invoiceId);
            cheque.addAllocatedInvoice(invoiceId);
            
            assertEquals(1, cheque.getAllocatedInvoiceIds().size());
        }
    }

    @Nested
    @DisplayName("Notes")
    class Notes {

        @Test
        @DisplayName("should update notes")
        void shouldUpdateNotes() {
            cheque.setNotes("Updated notes");
            assertEquals("Updated notes", cheque.getNotes());
        }
    }

    @Nested
    @DisplayName("Equality")
    class Equality {

        @Test
        @DisplayName("should be equal by id")
        void shouldBeEqualById() {
            Cheque other = new Cheque(chequeId, "CHQ-999", customerId, Money.of("100.00", "USD"),
                    "Other Bank", null, LocalDate.now(), null);
            assertEquals(cheque, other);
        }

        @Test
        @DisplayName("should not be equal when different ids")
        void shouldNotBeEqualWhenDifferentIds() {
            Cheque other = new Cheque(UUID.randomUUID(), "CHQ-123456", customerId, amount,
                    "Bank of America", "Main Branch", LocalDate.now(), null);
            assertNotEquals(cheque, other);
        }
    }
}
