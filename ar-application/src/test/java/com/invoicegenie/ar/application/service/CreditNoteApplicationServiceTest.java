package com.invoicegenie.ar.application.service;

import com.invoicegenie.ar.domain.model.customer.CustomerId;
import com.invoicegenie.ar.domain.model.payment.CreditNote;
import com.invoicegenie.ar.domain.model.payment.CreditNoteRepository;
import com.invoicegenie.ar.domain.service.CreditNoteService;
import com.invoicegenie.shared.domain.Money;
import com.invoicegenie.shared.domain.TenantId;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@DisplayName("CreditNoteApplicationService")
@ExtendWith(MockitoExtension.class)
class CreditNoteApplicationServiceTest {

    @Mock private CreditNoteRepository creditNoteRepository;
    private CreditNoteService creditNoteService;
    private CreditNoteApplicationService service;
    private TenantId tenantId;
    private UUID customerId;

    @BeforeEach
    void setUp() {
        creditNoteService = new CreditNoteService();
        service = new CreditNoteApplicationService(creditNoteService, creditNoteRepository);
        tenantId = TenantId.of(UUID.randomUUID());
        customerId = UUID.randomUUID();
    }

    @Nested
    @DisplayName("generateEarlyPaymentDiscount")
    class Generate {
        @Test
        @DisplayName("should generate and save credit note")
        void shouldGenerateAndSave() {
            Money discount = Money.of("20.00", "USD");
            UUID invoiceId = UUID.randomUUID();

            var result = service.generateEarlyPaymentDiscount(tenantId, customerId, discount, invoiceId);

            assertTrue(result.success());
            assertNotNull(result.creditNote());
            assertEquals(CreditNote.CreditNoteType.EARLY_PAYMENT_DISCOUNT, result.creditNote().getType());
            verify(creditNoteRepository).save(eq(tenantId), any(CreditNote.class));
        }
    }

    @Nested
    @DisplayName("apply")
    class Apply {
        @Test
        @DisplayName("should apply credit note to payment")
        void shouldApply() {
            CreditNote cn = new CreditNote(UUID.randomUUID(), "CN-001",
                    CustomerId.of(customerId), Money.of("20.00", "USD"),
                    CreditNote.CreditNoteType.EARLY_PAYMENT_DISCOUNT, null, "discount");
            UUID paymentId = UUID.randomUUID();
            when(creditNoteRepository.findByTenantAndId(tenantId, cn.getId())).thenReturn(Optional.of(cn));

            var result = service.apply(tenantId, cn.getId(), paymentId);

            assertTrue(result.isPresent());
            assertEquals(CreditNote.CreditNoteStatus.APPLIED, result.get().getStatus());
            assertEquals(paymentId, result.get().getAppliedToPaymentId());
            verify(creditNoteRepository).save(eq(tenantId), eq(cn));
        }

        @Test
        @DisplayName("should return empty when not found")
        void shouldReturnEmpty() {
            when(creditNoteRepository.findByTenantAndId(any(), any())).thenReturn(Optional.empty());
            assertTrue(service.apply(tenantId, UUID.randomUUID(), UUID.randomUUID()).isEmpty());
        }
    }

    @Nested
    @DisplayName("list")
    class ListNotes {
        @Test
        @DisplayName("should list by status")
        void shouldListByStatus() {
            when(creditNoteRepository.findByTenantAndStatus(tenantId, CreditNote.CreditNoteStatus.ISSUED))
                    .thenReturn(List.of());

            var result = service.list(tenantId, "ISSUED");

            assertTrue(result.success());
            assertTrue(result.creditNotes().isEmpty());
        }

        @Test
        @DisplayName("should reject invalid status")
        void shouldRejectInvalidStatus() {
            var result = service.list(tenantId, "BAD");
            assertFalse(result.success());
        }

        @Test
        @DisplayName("should list all credit notes when status is null")
        void shouldListAllWhenStatusNull() {
            when(creditNoteRepository.findByTenant(tenantId)).thenReturn(List.of());

            var result = service.list(tenantId, null);

            assertTrue(result.success());
            verify(creditNoteRepository).findByTenant(tenantId);
        }
    }
}
