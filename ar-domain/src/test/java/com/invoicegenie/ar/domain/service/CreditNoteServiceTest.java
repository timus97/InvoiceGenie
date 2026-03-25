package com.invoicegenie.ar.domain.service;

import com.invoicegenie.ar.domain.model.customer.CustomerId;
import com.invoicegenie.ar.domain.model.payment.CreditNote;
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

@DisplayName("CreditNoteService")
class CreditNoteServiceTest {

    private CreditNoteService service;
    private TenantId tenantId;
    private UUID customerId;

    @BeforeEach
    void setUp() {
        service = new CreditNoteService();
        tenantId = TenantId.of(UUID.randomUUID());
        customerId = UUID.randomUUID();
    }

    @Nested
    @DisplayName("Generate Early Payment Discount")
    class GenerateEarlyPaymentDiscount {

        @Test
        @DisplayName("should generate credit note for early payment discount")
        void shouldGenerateCreditNote() {
            Money discountAmount = Money.of("20.00", "USD");
            UUID invoiceId = UUID.randomUUID();
            
            CreditNoteService.CreditNoteResult result = service.generateEarlyPaymentDiscount(
                    tenantId, customerId, discountAmount, invoiceId);
            
            assertTrue(result.success());
            assertNotNull(result.creditNote());
            assertEquals(CreditNote.CreditNoteType.EARLY_PAYMENT_DISCOUNT, result.creditNote().getType());
            assertEquals(invoiceId, result.creditNote().getReferenceInvoiceId());
        }

        @Test
        @DisplayName("should set correct description")
        void shouldSetCorrectDescription() {
            Money discountAmount = Money.of("20.00", "USD");
            
            CreditNoteService.CreditNoteResult result = service.generateEarlyPaymentDiscount(
                    tenantId, customerId, discountAmount, UUID.randomUUID());
            
            assertEquals("2% early payment discount", result.creditNote().getDescription());
        }
    }

    @Nested
    @DisplayName("Calculate Credit Needed")
    class CalculateCreditNeeded {

        @Test
        @DisplayName("should calculate credit needed for short payment")
        void shouldCalculateCreditNeeded() {
            Money invoiceAmount = Money.of("1000.00", "USD");
            Money paymentAmount = Money.of("980.00", "USD");
            
            Money creditNeeded = service.calculateCreditNeeded(invoiceAmount, paymentAmount);
            
            assertEquals(0, new BigDecimal("20.00").compareTo(creditNeeded.getAmount()));
        }

        @Test
        @DisplayName("should return negative when overpaid")
        void shouldReturnNegativeWhenOverpaid() {
            Money invoiceAmount = Money.of("1000.00", "USD");
            Money paymentAmount = Money.of("1100.00", "USD");
            
            Money creditNeeded = service.calculateCreditNeeded(invoiceAmount, paymentAmount);
            
            assertTrue(creditNeeded.getAmount().signum() < 0);
        }
    }

    @Nested
    @DisplayName("Validate Application")
    class ValidateApplication {

        @Test
        @DisplayName("should validate correct application")
        void shouldValidateCorrectApplication() {
            CreditNote creditNote = new CreditNote(
                    UUID.randomUUID(), "CN-001", CustomerId.of(customerId),
                    Money.of("50.00", "USD"), CreditNote.CreditNoteType.EARLY_PAYMENT_DISCOUNT,
                    UUID.randomUUID(), "Test");
            
            Money amountToApply = Money.of("20.00", "USD");
            Money shortPayment = Money.of("30.00", "USD");
            
            assertTrue(service.validateApplication(creditNote, amountToApply, shortPayment));
        }

        @Test
        @DisplayName("should fail for null credit note")
        void shouldFailForNullCreditNote() {
            Money amountToApply = Money.of("20.00", "USD");
            Money shortPayment = Money.of("30.00", "USD");
            
            assertFalse(service.validateApplication(null, amountToApply, shortPayment));
        }

        @Test
        @DisplayName("should fail for zero amount to apply")
        void shouldFailForZeroAmount() {
            CreditNote creditNote = new CreditNote(
                    UUID.randomUUID(), "CN-001", CustomerId.of(customerId),
                    Money.of("50.00", "USD"), CreditNote.CreditNoteType.EARLY_PAYMENT_DISCOUNT,
                    UUID.randomUUID(), "Test");
            
            assertFalse(service.validateApplication(creditNote, Money.of("0.00", "USD"), Money.of("30.00", "USD")));
        }

        @Test
        @DisplayName("should fail when amount exceeds credit note")
        void shouldFailWhenAmountExceedsCreditNote() {
            CreditNote creditNote = new CreditNote(
                    UUID.randomUUID(), "CN-001", CustomerId.of(customerId),
                    Money.of("50.00", "USD"), CreditNote.CreditNoteType.EARLY_PAYMENT_DISCOUNT,
                    UUID.randomUUID(), "Test");
            
            Money amountToApply = Money.of("60.00", "USD");
            Money shortPayment = Money.of("100.00", "USD");
            
            assertFalse(service.validateApplication(creditNote, amountToApply, shortPayment));
        }

        @Test
        @DisplayName("should fail when amount exceeds short payment")
        void shouldFailWhenAmountExceedsShortPayment() {
            CreditNote creditNote = new CreditNote(
                    UUID.randomUUID(), "CN-001", CustomerId.of(customerId),
                    Money.of("50.00", "USD"), CreditNote.CreditNoteType.EARLY_PAYMENT_DISCOUNT,
                    UUID.randomUUID(), "Test");
            
            Money amountToApply = Money.of("40.00", "USD");
            Money shortPayment = Money.of("30.00", "USD");
            
            assertFalse(service.validateApplication(creditNote, amountToApply, shortPayment));
        }

        @Test
        @DisplayName("should fail for applied credit note")
        void shouldFailForAppliedCreditNote() {
            CreditNote creditNote = new CreditNote(
                    UUID.randomUUID(), "CN-001", CustomerId.of(customerId),
                    Money.of("50.00", "USD"), CreditNote.CreditNoteType.EARLY_PAYMENT_DISCOUNT,
                    UUID.randomUUID(), "Test");
            creditNote.apply(UUID.randomUUID());
            
            Money amountToApply = Money.of("20.00", "USD");
            Money shortPayment = Money.of("30.00", "USD");
            
            assertFalse(service.validateApplication(creditNote, amountToApply, shortPayment));
        }
    }

    @Nested
    @DisplayName("Find Credit Notes To Apply")
    class FindCreditNotesToApply {

        @Test
        @DisplayName("should return empty list (placeholder implementation)")
        void shouldReturnEmptyList() {
            List<CreditNote> result = service.findCreditNotesToApply(
                    tenantId, customerId, Money.of("20.00", "USD"));
            
            assertNotNull(result);
            assertTrue(result.isEmpty());
        }
    }
}
