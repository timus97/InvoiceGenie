package com.invoicegenie.ar.domain.model.payment;

import com.invoicegenie.ar.domain.model.customer.CustomerId;
import com.invoicegenie.ar.domain.model.invoice.InvoiceId;
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

@DisplayName("Payment Aggregate")
class PaymentTest {

    private PaymentId paymentId;
    private CustomerId customerId;
    private Money amount;
    private Payment payment;

    @BeforeEach
    void setUp() {
        paymentId = PaymentId.generate();
        customerId = CustomerId.of(UUID.randomUUID());
        amount = Money.of("1000.00", "USD");
        payment = new Payment(paymentId, "PAY-001", customerId, amount, LocalDate.now(), PaymentMethod.BANK_TRANSFER);
    }

    @Nested
    @DisplayName("Construction")
    class Construction {

        @Test
        @DisplayName("should create payment with required fields")
        void shouldCreateWithRequiredFields() {
            assertNotNull(payment.getId());
            assertEquals("PAY-001", payment.getPaymentNumber());
            assertEquals(customerId, payment.getCustomerId());
            assertEquals(amount, payment.getAmount());
            assertEquals(PaymentMethod.BANK_TRANSFER, payment.getMethod());
            assertEquals(PaymentStatus.RECEIVED, payment.getStatus());
        }

        @Test
        @DisplayName("should create payment with all fields for reconstitution")
        void shouldCreateWithAllFields() {
            Instant now = Instant.now();
            UUID bankAccountId = UUID.randomUUID();
            Payment fullPayment = new Payment(
                    paymentId, "PAY-002", customerId, amount,
                    LocalDate.now(), now, PaymentMethod.BANK_TRANSFER,
                    "REF-123", bankAccountId, "Notes",
                    PaymentStatus.RECEIVED, now, now, 1L, List.of()
            );
            
            assertEquals("PAY-002", fullPayment.getPaymentNumber());
            assertEquals("REF-123", fullPayment.getReference());
            assertEquals(bankAccountId, fullPayment.getBankAccountId());
            assertEquals("Notes", fullPayment.getNotes());
        }
    }

    @Nested
    @DisplayName("Allocations")
    class Allocations {

        @Test
        @DisplayName("should allocate to invoice")
        void shouldAllocateToInvoice() {
            InvoiceId invoiceId = InvoiceId.generate();
            UUID allocatedBy = UUID.randomUUID();
            
            PaymentAllocation alloc = payment.allocate(invoiceId, Money.of("500.00", "USD"), allocatedBy, "Partial payment");
            
            assertNotNull(alloc);
            assertEquals(1, payment.getAllocations().size());
            assertEquals(0, new BigDecimal("500.00").compareTo(payment.getAmountUnallocated().getAmount()));
        }

        @Test
        @DisplayName("should throw when allocation exceeds unallocated amount")
        void shouldThrowWhenAllocationExceeds() {
            InvoiceId invoiceId = InvoiceId.generate();
            
            assertThrows(IllegalStateException.class, () ->
                    payment.allocate(invoiceId, Money.of("2000.00", "USD"), UUID.randomUUID(), null));
        }

        @Test
        @DisplayName("should throw when currency mismatch")
        void shouldThrowWhenCurrencyMismatch() {
            InvoiceId invoiceId = InvoiceId.generate();
            
            assertThrows(IllegalArgumentException.class, () ->
                    payment.allocate(invoiceId, Money.of("500.00", "EUR"), UUID.randomUUID(), null));
        }

        @Test
        @DisplayName("should check if fully allocated")
        void shouldCheckIfFullyAllocated() {
            assertFalse(payment.isFullyAllocated());
            
            InvoiceId invoiceId = InvoiceId.generate();
            payment.allocate(invoiceId, Money.of("1000.00", "USD"), UUID.randomUUID(), null);
            
            assertTrue(payment.isFullyAllocated());
        }

        @Test
        @DisplayName("should calculate unallocated amount")
        void shouldCalculateUnallocated() {
            assertEquals(0, new BigDecimal("1000.00").compareTo(payment.getAmountUnallocated().getAmount()));
            
            InvoiceId invoiceId = InvoiceId.generate();
            payment.allocate(invoiceId, Money.of("300.00", "USD"), UUID.randomUUID(), null);
            
            assertEquals(0, new BigDecimal("700.00").compareTo(payment.getAmountUnallocated().getAmount()));
        }
    }

    @Nested
    @DisplayName("Status Changes")
    class StatusChanges {

        @Test
        @DisplayName("should reverse payment")
        void shouldReversePayment() {
            payment.reverse();
            assertEquals(PaymentStatus.REVERSED, payment.getStatus());
        }

        @Test
        @DisplayName("should refund payment")
        void shouldRefundPayment() {
            payment.refund();
            assertEquals(PaymentStatus.REFUNDED, payment.getStatus());
        }

        @Test
        @DisplayName("should throw when reversing non-received payment")
        void shouldThrowWhenReversingNonReceived() {
            payment.reverse();
            assertThrows(IllegalStateException.class, () -> payment.reverse());
        }

        @Test
        @DisplayName("should throw when refunding non-received payment")
        void shouldThrowWhenRefundingNonReceived() {
            payment.refund();
            assertThrows(IllegalStateException.class, () -> payment.refund());
        }
    }

    @Nested
    @DisplayName("Notes")
    class Notes {

        @Test
        @DisplayName("should update notes")
        void shouldUpdateNotes() {
            payment.setNotes("Updated notes");
            assertEquals("Updated notes", payment.getNotes());
        }
    }

    @Nested
    @DisplayName("Equality")
    class Equality {

        @Test
        @DisplayName("should be equal by id")
        void shouldBeEqualById() {
            Payment other = new Payment(paymentId, "PAY-002", customerId, Money.of("500.00", "USD"), 
                    LocalDate.now(), PaymentMethod.CASH);
            assertEquals(payment, other);
        }

        @Test
        @DisplayName("should not be equal when different ids")
        void shouldNotBeEqualWhenDifferentIds() {
            Payment other = new Payment(PaymentId.generate(), "PAY-001", customerId, amount,
                    LocalDate.now(), PaymentMethod.BANK_TRANSFER);
            assertNotEquals(payment, other);
        }
    }
}
