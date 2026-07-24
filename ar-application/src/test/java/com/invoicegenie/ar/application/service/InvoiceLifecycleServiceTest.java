package com.invoicegenie.ar.application.service;

import com.invoicegenie.ar.application.port.inbound.ApplyInvoicePaymentUseCase;
import com.invoicegenie.ar.domain.model.invoice.Invoice;
import com.invoicegenie.ar.domain.model.invoice.InvoiceId;
import com.invoicegenie.ar.domain.model.invoice.InvoiceLine;
import com.invoicegenie.ar.domain.model.invoice.InvoiceRepository;
import com.invoicegenie.ar.domain.model.invoice.InvoiceVersionRepository;
import com.invoicegenie.ar.domain.model.invoice.InvoiceStatus;
import com.invoicegenie.ar.domain.model.ledger.LedgerEntry;
import com.invoicegenie.ar.domain.model.ledger.LedgerRepository;
import com.invoicegenie.ar.domain.model.outbox.AuditRepository;
import com.invoicegenie.ar.domain.service.LedgerService;
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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@DisplayName("InvoiceLifecycleService")
@ExtendWith(MockitoExtension.class)
class InvoiceLifecycleServiceTest {

    @Mock private InvoiceRepository invoiceRepository;
    @Mock private AuditRepository auditRepository;
    @Mock private LedgerRepository ledgerRepository;
    @Mock private InvoiceVersionRepository invoiceVersionRepository;
    @Mock private ApplyInvoicePaymentUseCase applyInvoicePaymentUseCase;

    private InvoiceLifecycleService service;
    private TenantId tenantId;
    private InvoiceId invoiceId;

    @BeforeEach
    void setUp() {
        service = new InvoiceLifecycleService(
                invoiceRepository,
                auditRepository,
                applyInvoicePaymentUseCase,
                new LedgerService(),
                ledgerRepository,
                invoiceVersionRepository);
        tenantId = TenantId.of(UUID.randomUUID());
        invoiceId = InvoiceId.generate();
    }

    private Invoice createDraftInvoice() {
        Invoice invoice = new Invoice(invoiceId, "INV-001", null, "CUST001", "USD",
                LocalDate.now(), LocalDate.now().plusDays(30), List.of());
        invoice.addLine(new InvoiceLine(1, "Service", Money.of("1000.00", "USD")));
        return invoice;
    }

    private Invoice createIssuedInvoice() {
        Invoice invoice = createDraftInvoice();
        invoice.issue();
        return invoice;
    }

    private Invoice createOverdueInvoice() {
        Invoice invoice = new Invoice(invoiceId, "INV-001", null, "CUST001", "USD",
                LocalDate.now().minusDays(60), LocalDate.now().minusDays(30), List.of());
        invoice.addLine(new InvoiceLine(1, "Service", Money.of("1000.00", "USD")));
        invoice.issue();
        return invoice;
    }

    @Nested
    @DisplayName("Issue")
    class Issue {

        @Test
        @DisplayName("should issue draft invoice and post ledger")
        void shouldIssueDraftInvoice() {
            Invoice invoice = createDraftInvoice();
            when(invoiceRepository.findByTenantAndId(tenantId, invoiceId)).thenReturn(Optional.of(invoice));

            Optional<Invoice> result = service.issue(tenantId, invoiceId);

            assertTrue(result.isPresent());
            assertEquals(InvoiceStatus.ISSUED, result.get().getStatus());
            verify(invoiceRepository).save(tenantId, invoice);
            verify(auditRepository).save(eq(tenantId), any());
            @SuppressWarnings("unchecked")
            ArgumentCaptor<List<LedgerEntry>> captor = ArgumentCaptor.forClass(List.class);
            verify(ledgerRepository).saveAll(eq(tenantId), captor.capture());
            assertEquals(2, captor.getValue().size());
        }

        @Test
        @DisplayName("should return empty when invoice not found")
        void shouldReturnEmptyWhenNotFound() {
            when(invoiceRepository.findByTenantAndId(tenantId, invoiceId)).thenReturn(Optional.empty());

            Optional<Invoice> result = service.issue(tenantId, invoiceId);

            assertFalse(result.isPresent());
            verify(invoiceRepository, never()).save(any(), any());
            verify(auditRepository, never()).save(any(), any());
            verifyNoInteractions(ledgerRepository);
        }
    }

    @Nested
    @DisplayName("Mark Overdue")
    class MarkOverdue {

        @Test
        @DisplayName("should mark invoice as overdue when past due date")
        void shouldMarkInvoiceAsOverdue() {
            Invoice invoice = createOverdueInvoice();
            when(invoiceRepository.findByTenantAndId(tenantId, invoiceId)).thenReturn(Optional.of(invoice));

            Optional<Invoice> result = service.markOverdue(tenantId, invoiceId, LocalDate.now());

            assertTrue(result.isPresent());
            assertEquals(InvoiceStatus.OVERDUE, result.get().getStatus());
            verify(invoiceRepository).save(tenantId, invoice);
            verify(auditRepository).save(eq(tenantId), any());
        }

        @Test
        @DisplayName("should return empty when invoice not found")
        void shouldReturnEmptyWhenNotFound() {
            when(invoiceRepository.findByTenantAndId(tenantId, invoiceId)).thenReturn(Optional.empty());

            Optional<Invoice> result = service.markOverdue(tenantId, invoiceId, LocalDate.now());

            assertFalse(result.isPresent());
            verify(auditRepository, never()).save(any(), any());
        }
    }

    @Nested
    @DisplayName("Write Off")
    class WriteOff {

        @Test
        @DisplayName("should write off overdue invoice and post ledger")
        void shouldWriteOffOverdueInvoiceWithReason() {
            Invoice invoice = createOverdueInvoice();
            invoice.markOverdue(LocalDate.now());
            when(invoiceRepository.findByTenantAndId(tenantId, invoiceId)).thenReturn(Optional.of(invoice));

            Optional<Invoice> result = service.writeOff(tenantId, invoiceId, "Bad debt");

            assertTrue(result.isPresent());
            assertEquals(InvoiceStatus.WRITTEN_OFF, result.get().getStatus());
            verify(invoiceRepository).save(tenantId, invoice);
            verify(auditRepository).save(eq(tenantId), any());
            @SuppressWarnings("unchecked")
            ArgumentCaptor<List<LedgerEntry>> captor = ArgumentCaptor.forClass(List.class);
            verify(ledgerRepository).saveAll(eq(tenantId), captor.capture());
            assertEquals(2, captor.getValue().size());
        }

        @Test
        @DisplayName("should return empty when invoice not found")
        void shouldReturnEmptyWhenNotFound() {
            when(invoiceRepository.findByTenantAndId(tenantId, invoiceId)).thenReturn(Optional.empty());

            Optional<Invoice> result = service.writeOff(tenantId, invoiceId, "Bad debt");

            assertFalse(result.isPresent());
            verify(auditRepository, never()).save(any(), any());
            verifyNoInteractions(ledgerRepository);
        }
    }

    @Nested
    @DisplayName("Apply Payment")
    class ApplyPayment {

        @Test
        @DisplayName("should delegate full payment to ApplyInvoicePaymentUseCase")
        void shouldMarkInvoiceAsFullyPaid() {
            Invoice invoice = createIssuedInvoice();
            invoice.recordPaymentApplied(invoice.getBalanceDue());
            when(applyInvoicePaymentUseCase.apply(eq(tenantId), eq(invoiceId), any()))
                    .thenReturn(Optional.of(invoice));

            Optional<Invoice> result = service.applyPayment(tenantId, invoiceId, true);

            assertTrue(result.isPresent());
            assertEquals(InvoiceStatus.PAID, result.get().getStatus());
            verify(applyInvoicePaymentUseCase).apply(eq(tenantId), eq(invoiceId),
                    eq(new ApplyInvoicePaymentUseCase.ApplyPaymentCommand(null, true)));
        }

        @Test
        @DisplayName("should delegate partial flag to ApplyInvoicePaymentUseCase")
        void shouldMarkInvoiceAsPartiallyPaid() {
            Invoice invoice = createIssuedInvoice();
            invoice.recordPaymentApplied(Money.of("100.00", "USD"));
            when(applyInvoicePaymentUseCase.apply(eq(tenantId), eq(invoiceId), any()))
                    .thenReturn(Optional.of(invoice));

            Optional<Invoice> result = service.applyPayment(tenantId, invoiceId, false);

            assertTrue(result.isPresent());
            assertEquals(InvoiceStatus.PARTIALLY_PAID, result.get().getStatus());
            verify(applyInvoicePaymentUseCase).apply(eq(tenantId), eq(invoiceId),
                    eq(new ApplyInvoicePaymentUseCase.ApplyPaymentCommand(null, false)));
        }

        @Test
        @DisplayName("should return empty when invoice not found")
        void shouldReturnEmptyWhenNotFound() {
            when(applyInvoicePaymentUseCase.apply(eq(tenantId), eq(invoiceId), any()))
                    .thenReturn(Optional.empty());

            Optional<Invoice> result = service.applyPayment(tenantId, invoiceId, true);

            assertFalse(result.isPresent());
        }
    }

    @Nested
    @DisplayName("Update Due Date")
    class UpdateDueDate {

        @Test
        @DisplayName("should update due date on draft invoice")
        void shouldUpdateDueDate() {
            Invoice invoice = createDraftInvoice();
            LocalDate newDueDate = LocalDate.now().plusDays(60);
            when(invoiceRepository.findByTenantAndId(tenantId, invoiceId)).thenReturn(Optional.of(invoice));

            Optional<Invoice> result = service.updateDueDate(tenantId, invoiceId, newDueDate);

            assertTrue(result.isPresent());
            assertEquals(newDueDate, result.get().getDueDate());
            verify(invoiceRepository).save(tenantId, invoice);
        }

        @Test
        @DisplayName("should return empty when invoice not found")
        void shouldReturnEmptyWhenNotFound() {
            when(invoiceRepository.findByTenantAndId(tenantId, invoiceId)).thenReturn(Optional.empty());

            Optional<Invoice> result = service.updateDueDate(tenantId, invoiceId, LocalDate.now().plusDays(60));

            assertFalse(result.isPresent());
        }
    }

    @Nested
    @DisplayName("Reopen")
    class Reopen {

        @Test
        @DisplayName("should reopen paid invoice")
        void shouldReopenPaidInvoice() {
            Invoice invoice = createIssuedInvoice();
            invoice.applyPaymentStatus(true);
            when(invoiceRepository.findByTenantAndId(tenantId, invoiceId)).thenReturn(Optional.of(invoice));

            Optional<Invoice> result = service.reopen(tenantId, invoiceId, "Cheque bounced");

            assertTrue(result.isPresent());
            assertEquals(InvoiceStatus.ISSUED, result.get().getStatus());
            verify(invoiceRepository).save(tenantId, invoice);
        }

        @Test
        @DisplayName("should return empty when invoice not found")
        void shouldReturnEmptyWhenNotFound() {
            when(invoiceRepository.findByTenantAndId(tenantId, invoiceId)).thenReturn(Optional.empty());

            Optional<Invoice> result = service.reopen(tenantId, invoiceId, "Reason");

            assertFalse(result.isPresent());
        }
    }
}
