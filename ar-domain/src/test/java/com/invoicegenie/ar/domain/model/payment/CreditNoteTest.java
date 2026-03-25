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
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("CreditNote Aggregate")
class CreditNoteTest {

    private UUID creditNoteId;
    private CustomerId customerId;
    private Money amount;
    private CreditNote creditNote;

    @BeforeEach
    void setUp() {
        creditNoteId = UUID.randomUUID();
        customerId = CustomerId.of(UUID.randomUUID());
        amount = Money.of("100.00", "USD");
        creditNote = new CreditNote(creditNoteId, "CN-001", customerId, amount,
                CreditNote.CreditNoteType.EARLY_PAYMENT_DISCOUNT, UUID.randomUUID(), "Early payment discount");
    }

    @Nested
    @DisplayName("Construction")
    class Construction {

        @Test
        @DisplayName("should create credit note with required fields")
        void shouldCreateWithRequiredFields() {
            assertNotNull(creditNote.getId());
            assertEquals("CN-001", creditNote.getCreditNoteNumber());
            assertEquals(customerId, creditNote.getCustomerId());
            assertEquals(amount, creditNote.getAmount());
            assertEquals(CreditNote.CreditNoteType.EARLY_PAYMENT_DISCOUNT, creditNote.getType());
            assertEquals(CreditNote.CreditNoteStatus.ISSUED, creditNote.getStatus());
        }

        @Test
        @DisplayName("should throw when credit note number is blank")
        void shouldThrowWhenNumberBlank() {
            assertThrows(IllegalArgumentException.class, () ->
                    new CreditNote(creditNoteId, "", customerId, amount,
                            CreditNote.CreditNoteType.ADJUSTMENT, null, null));
        }

        @Test
        @DisplayName("should throw when amount is not positive")
        void shouldThrowWhenAmountNotPositive() {
            assertThrows(IllegalArgumentException.class, () ->
                    new CreditNote(creditNoteId, "CN-001", customerId, Money.of("0.00", "USD"),
                            CreditNote.CreditNoteType.ADJUSTMENT, null, null));
        }

        @Test
        @DisplayName("should create credit note with all fields for reconstitution")
        void shouldCreateWithAllFields() {
            Instant now = Instant.now();
            CreditNote fullNote = new CreditNote(
                    creditNoteId, "CN-002", customerId, amount,
                    CreditNote.CreditNoteType.ADJUSTMENT, UUID.randomUUID(), "Description",
                    CreditNote.CreditNoteStatus.ISSUED, LocalDate.now(), null,
                    LocalDate.now().plusYears(1), null, "Notes",
                    now, now
            );
            
            assertEquals("CN-002", fullNote.getCreditNoteNumber());
            assertEquals(CreditNote.CreditNoteType.ADJUSTMENT, fullNote.getType());
        }
    }

    @Nested
    @DisplayName("Application")
    class Application {

        @Test
        @DisplayName("should apply credit note to payment")
        void shouldApplyToPayment() {
            UUID paymentId = UUID.randomUUID();
            creditNote.apply(paymentId);
            
            assertEquals(CreditNote.CreditNoteStatus.APPLIED, creditNote.getStatus());
            assertEquals(paymentId, creditNote.getAppliedToPaymentId());
            assertNotNull(creditNote.getAppliedDate());
        }

        @Test
        @DisplayName("should check if can apply")
        void shouldCheckIfCanApply() {
            assertTrue(creditNote.canApply());
            
            creditNote.apply(UUID.randomUUID());
            assertFalse(creditNote.canApply());
        }

        @Test
        @DisplayName("should not apply if already applied")
        void shouldNotApplyIfAlreadyApplied() {
            creditNote.apply(UUID.randomUUID());
            assertThrows(IllegalStateException.class, () -> creditNote.apply(UUID.randomUUID()));
        }
    }

    @Nested
    @DisplayName("Voiding")
    class Voiding {

        @Test
        @DisplayName("should void credit note")
        void shouldVoidCreditNote() {
            creditNote.voidNote("Customer request");
            assertEquals(CreditNote.CreditNoteStatus.VOIDED, creditNote.getStatus());
            assertTrue(creditNote.getNotes().contains("[VOIDED]"));
        }

        @Test
        @DisplayName("should not void applied credit note")
        void shouldNotVoidApplied() {
            creditNote.apply(UUID.randomUUID());
            assertThrows(IllegalStateException.class, () -> creditNote.voidNote("Reason"));
        }
    }

    @Nested
    @DisplayName("Expiry")
    class Expiry {

        @Test
        @DisplayName("should check if expired")
        void shouldCheckIfExpired() {
            assertFalse(creditNote.isExpired());
        }

        @Test
        @DisplayName("should not apply expired credit note")
        void shouldNotApplyExpired() {
            // Create credit note with past expiry
            Instant now = Instant.now();
            CreditNote expiredNote = new CreditNote(
                    creditNoteId, "CN-EXP", customerId, amount,
                    CreditNote.CreditNoteType.ADJUSTMENT, null, "Description",
                    CreditNote.CreditNoteStatus.ISSUED, LocalDate.now().minusYears(2), null,
                    LocalDate.now().minusDays(1), null, "Notes",
                    now, now
            );
            
            assertFalse(expiredNote.canApply());
        }
    }

    @Nested
    @DisplayName("Remaining Balance")
    class RemainingBalance {

        @Test
        @DisplayName("should return full amount when not applied")
        void shouldReturnFullAmountWhenNotApplied() {
            assertEquals(amount, creditNote.getRemainingBalance());
        }

        @Test
        @DisplayName("should return zero when applied")
        void shouldReturnZeroWhenApplied() {
            creditNote.apply(UUID.randomUUID());
            assertEquals(0, new BigDecimal("0.00").compareTo(creditNote.getRemainingBalance().getAmount()));
        }
    }

    @Nested
    @DisplayName("Factory Methods")
    class FactoryMethods {

        @Test
        @DisplayName("should create early payment discount credit note")
        void shouldCreateEarlyPaymentDiscount() {
            UUID invoiceId = UUID.randomUUID();
            CreditNote epdNote = CreditNote.forEarlyPaymentDiscount(creditNoteId, customerId, amount, invoiceId);
            
            assertEquals(CreditNote.CreditNoteType.EARLY_PAYMENT_DISCOUNT, epdNote.getType());
            assertEquals(invoiceId, epdNote.getReferenceInvoiceId());
        }
    }

    @Nested
    @DisplayName("Equality")
    class Equality {

        @Test
        @DisplayName("should be equal by id")
        void shouldBeEqualById() {
            CreditNote other = new CreditNote(creditNoteId, "CN-999", customerId, Money.of("500.00", "USD"),
                    CreditNote.CreditNoteType.ADJUSTMENT, null, null);
            assertEquals(creditNote, other);
        }

        @Test
        @DisplayName("should not be equal when different ids")
        void shouldNotBeEqualWhenDifferentIds() {
            CreditNote other = new CreditNote(UUID.randomUUID(), "CN-001", customerId, amount,
                    CreditNote.CreditNoteType.EARLY_PAYMENT_DISCOUNT, null, null);
            assertNotEquals(creditNote, other);
        }
    }
}
