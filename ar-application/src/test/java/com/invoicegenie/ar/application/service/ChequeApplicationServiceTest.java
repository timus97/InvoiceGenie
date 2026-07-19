package com.invoicegenie.ar.application.service;

import com.invoicegenie.ar.application.port.inbound.ChequeUseCase;
import com.invoicegenie.ar.application.port.inbound.InvoiceLifecycleUseCase;
import com.invoicegenie.ar.domain.model.customer.CustomerId;
import com.invoicegenie.ar.domain.model.invoice.InvoiceId;
import com.invoicegenie.ar.domain.model.payment.Cheque;
import com.invoicegenie.ar.domain.model.payment.ChequeRepository;
import com.invoicegenie.ar.domain.model.payment.ChequeStatus;
import com.invoicegenie.ar.domain.service.ChequeService;
import com.invoicegenie.shared.domain.Money;
import com.invoicegenie.shared.domain.TenantId;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@DisplayName("ChequeApplicationService")
@ExtendWith(MockitoExtension.class)
class ChequeApplicationServiceTest {

    @Mock private ChequeRepository chequeRepository;
    @Mock private InvoiceLifecycleUseCase invoiceLifecycleUseCase;
    private ChequeService chequeService;
    private ChequeApplicationService service;
    private TenantId tenantId;
    private UUID customerUuid;

    @BeforeEach
    void setUp() {
        chequeService = new ChequeService();
        service = new ChequeApplicationService(chequeService, chequeRepository, invoiceLifecycleUseCase);
        tenantId = TenantId.of(UUID.randomUUID());
        customerUuid = UUID.randomUUID();
    }

    private Cheque newCheque() {
        return new Cheque(UUID.randomUUID(), "CHQ-001", new CustomerId(customerUuid),
                Money.of("500.00", "USD"), "Bank of Test", "Main", LocalDate.now(), null);
    }

    @Nested
    @DisplayName("create")
    class Create {
        @Test
        @DisplayName("should create and save cheque")
        void shouldCreate() {
            Cheque cheque = service.create(tenantId, new ChequeUseCase.CreateChequeCommand(
                    "CHQ-001", customerUuid.toString(), Money.of("500.00", "USD"),
                    "Bank of Test", "Main", LocalDate.now(), "notes"));

            assertEquals(ChequeStatus.RECEIVED, cheque.getStatus());
            assertEquals("CHQ-001", cheque.getChequeNumber());
            verify(chequeRepository).save(eq(tenantId), any(Cheque.class));
        }
    }

    @Nested
    @DisplayName("deposit / clear")
    class DepositClear {
        @Test
        @DisplayName("should deposit received cheque")
        void shouldDeposit() {
            Cheque cheque = newCheque();
            when(chequeRepository.findByTenantAndId(eq(tenantId), eq(cheque.getId())))
                    .thenReturn(Optional.of(cheque));

            var result = service.deposit(tenantId, cheque.getId());

            assertTrue(result.isPresent());
            assertTrue(result.get().success());
            assertEquals(ChequeStatus.DEPOSITED, result.get().cheque().getStatus());
            verify(chequeRepository).save(eq(tenantId), eq(cheque));
        }

        @Test
        @DisplayName("should return empty when cheque not found")
        void shouldReturnEmptyWhenNotFound() {
            when(chequeRepository.findByTenantAndId(any(), any())).thenReturn(Optional.empty());

            assertTrue(service.deposit(tenantId, UUID.randomUUID()).isEmpty());
        }

        @Test
        @DisplayName("should clear deposited cheque")
        void shouldClear() {
            Cheque cheque = newCheque();
            cheque.deposit();
            when(chequeRepository.findByTenantAndId(eq(tenantId), eq(cheque.getId())))
                    .thenReturn(Optional.of(cheque));

            var result = service.clear(tenantId, cheque.getId());

            assertTrue(result.isPresent());
            assertTrue(result.get().success());
            assertEquals(ChequeStatus.CLEARED, result.get().cheque().getStatus());
            assertFalse(result.get().ledgerEntries().isEmpty());
        }
    }

    @Nested
    @DisplayName("bounce")
    class Bounce {
        @Test
        @DisplayName("should bounce deposited cheque and save")
        void shouldBounceAndReopen() {
            Cheque cheque = newCheque();
            cheque.deposit();
            when(chequeRepository.findByTenantAndId(eq(tenantId), eq(cheque.getId())))
                    .thenReturn(Optional.of(cheque));

            var result = service.bounce(tenantId, cheque.getId(), "NSF");

            assertTrue(result.isPresent());
            assertTrue(result.get().success());
            assertEquals(ChequeStatus.BOUNCED, result.get().cheque().getStatus());
            verify(chequeRepository).save(eq(tenantId), eq(cheque));
            // No allocated invoices on a plain received→deposited cheque
            assertTrue(result.get().affectedInvoiceIds().isEmpty());
            verifyNoInteractions(invoiceLifecycleUseCase);
        }
    }

    @Nested
    @DisplayName("list")
    class ListCheques {
        @Test
        @DisplayName("should list by status")
        void shouldListByStatus() {
            when(chequeRepository.findByTenantAndStatus(tenantId, ChequeStatus.RECEIVED))
                    .thenReturn(List.of(newCheque()));

            var result = service.list(tenantId, "RECEIVED");

            assertTrue(result.success());
            assertEquals(1, result.cheques().size());
        }

        @Test
        @DisplayName("should reject invalid status")
        void shouldRejectInvalidStatus() {
            var result = service.list(tenantId, "NOPE");
            assertFalse(result.success());
        }
    }
}
