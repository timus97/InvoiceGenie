package com.invoicegenie.ar.domain.service;

import com.invoicegenie.ar.domain.model.customer.CustomerId;
import com.invoicegenie.ar.domain.model.payment.Cheque;
import com.invoicegenie.ar.domain.model.payment.ChequeStatus;
import com.invoicegenie.shared.domain.Money;
import com.invoicegenie.shared.domain.TenantId;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("ChequeService")
class ChequeServiceTest {

    private ChequeService service;
    private TenantId tenantId;
    private CustomerId customerId;

    @BeforeEach
    void setUp() {
        service = new ChequeService();
        tenantId = TenantId.of(UUID.randomUUID());
        customerId = CustomerId.of(UUID.randomUUID());
    }

    private Cheque createCheque() {
        return new Cheque(UUID.randomUUID(), "CHQ-123456", customerId,
                Money.of("5000.00", "USD"), "Bank of America", "Main Branch",
                LocalDate.now(), "Test cheque");
    }

    @Nested
    @DisplayName("Deposit")
    class Deposit {

        @Test
        @DisplayName("should deposit cheque successfully")
        void shouldDepositSuccessfully() {
            Cheque cheque = createCheque();
            
            ChequeService.DepositResult result = service.deposit(tenantId, cheque);
            
            assertTrue(result.success());
            assertEquals(ChequeStatus.DEPOSITED, result.cheque().getStatus());
            assertEquals("Cheque deposited successfully", result.message());
        }

        @Test
        @DisplayName("should fail to deposit already deposited cheque")
        void shouldFailToDepositAlreadyDeposited() {
            Cheque cheque = createCheque();
            cheque.deposit();
            
            ChequeService.DepositResult result = service.deposit(tenantId, cheque);
            
            assertFalse(result.success());
        }
    }

    @Nested
    @DisplayName("Clear")
    class Clear {

        @Test
        @DisplayName("should clear cheque and create ledger entries")
        void shouldClearCheque() {
            Cheque cheque = createCheque();
            cheque.deposit();
            
            ChequeService.ClearResult result = service.clear(tenantId, cheque);
            
            assertTrue(result.success());
            assertEquals(ChequeStatus.CLEARED, result.cheque().getStatus());
            assertEquals(2, result.ledgerEntries().size());
            // paymentId may be null if not linked to a payment yet
        }

        @Test
        @DisplayName("should fail to clear non-deposited cheque")
        void shouldFailToClearNonDeposited() {
            Cheque cheque = createCheque();
            
            ChequeService.ClearResult result = service.clear(tenantId, cheque);
            
            assertFalse(result.success());
        }
    }

    @Nested
    @DisplayName("Bounce")
    class Bounce {

        @Test
        @DisplayName("should bounce cheque and create reverse entries")
        void shouldBounceCheque() {
            Cheque cheque = createCheque();
            cheque.deposit();
            
            ChequeService.BounceResult result = service.bounce(tenantId, cheque, "Insufficient funds");
            
            assertTrue(result.success());
            assertEquals(ChequeStatus.BOUNCED, result.cheque().getStatus());
            assertEquals(2, result.reverseEntries().size());
            assertEquals("Insufficient funds", result.cheque().getBounceReason());
        }

        @Test
        @DisplayName("should fail to bounce non-deposited cheque")
        void shouldFailToBounceNonDeposited() {
            Cheque cheque = createCheque();
            
            ChequeService.BounceResult result = service.bounce(tenantId, cheque, "Reason");
            
            assertFalse(result.success());
        }
    }

    @Nested
    @DisplayName("Valid Transitions")
    class ValidTransitions {

        @Test
        @DisplayName("should return DEPOSITED for RECEIVED status")
        void shouldReturnDepositedForReceived() {
            List<ChequeStatus> transitions = service.getValidTransitions(ChequeStatus.RECEIVED);
            
            assertEquals(1, transitions.size());
            assertTrue(transitions.contains(ChequeStatus.DEPOSITED));
        }

        @Test
        @DisplayName("should return CLEARED and BOUNCED for DEPOSITED status")
        void shouldReturnClearedAndBouncedForDeposited() {
            List<ChequeStatus> transitions = service.getValidTransitions(ChequeStatus.DEPOSITED);
            
            assertEquals(2, transitions.size());
            assertTrue(transitions.contains(ChequeStatus.CLEARED));
            assertTrue(transitions.contains(ChequeStatus.BOUNCED));
        }

        @Test
        @DisplayName("should return empty for terminal states")
        void shouldReturnEmptyForTerminalStates() {
            assertTrue(service.getValidTransitions(ChequeStatus.CLEARED).isEmpty());
            assertTrue(service.getValidTransitions(ChequeStatus.BOUNCED).isEmpty());
        }
    }

    @Nested
    @DisplayName("Validation")
    class Validation {

        @Test
        @DisplayName("should validate correct cheque data")
        void shouldValidateCorrectData() {
            assertTrue(service.validateCheque(
                    "CHQ-123", "Bank", LocalDate.now(), Money.of("100.00", "USD")));
        }

        @Test
        @DisplayName("should fail for blank cheque number")
        void shouldFailForBlankChequeNumber() {
            assertFalse(service.validateCheque(
                    "", "Bank", LocalDate.now(), Money.of("100.00", "USD")));
        }

        @Test
        @DisplayName("should fail for blank bank name")
        void shouldFailForBlankBankName() {
            assertFalse(service.validateCheque(
                    "CHQ-123", "", LocalDate.now(), Money.of("100.00", "USD")));
        }

        @Test
        @DisplayName("should fail for null cheque date")
        void shouldFailForNullChequeDate() {
            assertFalse(service.validateCheque(
                    "CHQ-123", "Bank", null, Money.of("100.00", "USD")));
        }

        @Test
        @DisplayName("should fail for zero amount")
        void shouldFailForZeroAmount() {
            assertFalse(service.validateCheque(
                    "CHQ-123", "Bank", LocalDate.now(), Money.of("0.00", "USD")));
        }
    }
}
