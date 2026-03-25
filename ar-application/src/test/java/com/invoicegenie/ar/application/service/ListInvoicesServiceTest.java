package com.invoicegenie.ar.application.service;

import com.invoicegenie.ar.application.port.inbound.ListInvoicesUseCase;
import com.invoicegenie.ar.domain.model.invoice.Invoice;
import com.invoicegenie.ar.domain.model.invoice.InvoiceId;
import com.invoicegenie.ar.domain.model.invoice.InvoiceLine;
import com.invoicegenie.ar.domain.model.invoice.InvoiceRepository;
import com.invoicegenie.ar.domain.model.invoice.InvoiceStatus;
import com.invoicegenie.shared.domain.Money;
import com.invoicegenie.shared.domain.TenantId;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.*;

@DisplayName("ListInvoicesService")
@ExtendWith(MockitoExtension.class)
class ListInvoicesServiceTest {

    @Mock
    private InvoiceRepository invoiceRepository;

    private ListInvoicesService service;
    private TenantId tenantId;

    @BeforeEach
    void setUp() {
        service = new ListInvoicesService(invoiceRepository);
        tenantId = TenantId.of(UUID.randomUUID());
    }

    private Invoice createInvoice(String number, InvoiceStatus status) {
        InvoiceId id = InvoiceId.generate();
        Invoice invoice = new Invoice(id, number, "CUST001", "USD",
                LocalDate.now(), LocalDate.now().plusDays(30), List.of());
        invoice.addLine(new InvoiceLine(1, "Service", Money.of("1000.00", "USD")));
        if (status != InvoiceStatus.DRAFT) {
            invoice.issue();
        }
        if (status == InvoiceStatus.PAID) {
            invoice.applyPaymentStatus(true);
        }
        return invoice;
    }

    @Nested
    @DisplayName("List Invoices")
    class ListInvoices {

        @Test
        @DisplayName("should list invoices with default limit")
        void shouldListInvoicesWithDefaultLimit() {
            Invoice invoice = createInvoice("INV-001", InvoiceStatus.ISSUED);
            InvoiceRepository.Page page = new InvoiceRepository.Page(List.of(invoice), Optional.empty());
            
            when(invoiceRepository.findByTenant(eq(tenantId), eq(20), isNull())).thenReturn(page);

            ListInvoicesUseCase.PageResult result = service.list(tenantId, 20, null, null);

            assertEquals(1, result.items().size());
            assertFalse(result.nextCursor().isPresent());
            assertEquals(1, result.total());
        }

        @Test
        @DisplayName("should enforce minimum limit of 1")
        void shouldEnforceMinimumLimit() {
            InvoiceRepository.Page page = new InvoiceRepository.Page(List.of(), Optional.empty());
            
            when(invoiceRepository.findByTenant(eq(tenantId), eq(1), isNull())).thenReturn(page);

            ListInvoicesUseCase.PageResult result = service.list(tenantId, 0, null, null);

            assertEquals(0, result.items().size());
        }

        @Test
        @DisplayName("should enforce maximum limit of 100")
        void shouldEnforceMaximumLimit() {
            InvoiceRepository.Page page = new InvoiceRepository.Page(List.of(), Optional.empty());
            
            when(invoiceRepository.findByTenant(eq(tenantId), eq(100), isNull())).thenReturn(page);

            ListInvoicesUseCase.PageResult result = service.list(tenantId, 500, null, null);

            assertEquals(0, result.items().size());
        }

        @Test
        @DisplayName("should filter by status")
        void shouldFilterByStatus() {
            Invoice invoice1 = createInvoice("INV-001", InvoiceStatus.ISSUED);
            Invoice invoice2 = createInvoice("INV-002", InvoiceStatus.PAID);
            InvoiceRepository.Page page = new InvoiceRepository.Page(List.of(invoice1, invoice2), Optional.empty());
            
            when(invoiceRepository.findByTenant(eq(tenantId), eq(20), isNull())).thenReturn(page);

            ListInvoicesUseCase.PageResult result = service.list(tenantId, 20, null, InvoiceStatus.PAID);

            assertEquals(1, result.items().size());
            assertEquals(InvoiceStatus.PAID, result.items().get(0).getStatus());
        }

        @Test
        @DisplayName("should return next cursor when available")
        void shouldReturnNextCursor() {
            Invoice invoice = createInvoice("INV-001", InvoiceStatus.ISSUED);
            InvoiceRepository.PageCursor nextCursor = new InvoiceRepository.PageCursor(
                    Instant.now(), InvoiceId.generate());
            InvoiceRepository.Page page = new InvoiceRepository.Page(List.of(invoice), Optional.of(nextCursor));
            
            when(invoiceRepository.findByTenant(eq(tenantId), eq(20), isNull())).thenReturn(page);

            ListInvoicesUseCase.PageResult result = service.list(tenantId, 20, null, null);

            assertTrue(result.nextCursor().isPresent());
        }
    }

    @Nested
    @DisplayName("Cursor Handling")
    class CursorHandling {

        @Test
        @DisplayName("should handle null cursor")
        void shouldHandleNullCursor() {
            InvoiceRepository.Page page = new InvoiceRepository.Page(List.of(), Optional.empty());
            
            when(invoiceRepository.findByTenant(eq(tenantId), eq(20), isNull())).thenReturn(page);

            ListInvoicesUseCase.PageResult result = service.list(tenantId, 20, null, null);

            assertEquals(0, result.items().size());
        }

        @Test
        @DisplayName("should handle blank cursor")
        void shouldHandleBlankCursor() {
            InvoiceRepository.Page page = new InvoiceRepository.Page(List.of(), Optional.empty());
            
            when(invoiceRepository.findByTenant(eq(tenantId), eq(20), isNull())).thenReturn(page);

            ListInvoicesUseCase.PageResult result = service.list(tenantId, 20, "  ", null);

            assertEquals(0, result.items().size());
        }

        @Test
        @DisplayName("should decode valid cursor")
        void shouldDecodeValidCursor() {
            InvoiceId id = InvoiceId.generate();
            Instant createdAt = Instant.now();
            String rawCursor = createdAt.toString() + "|" + id.getValue().toString();
            String encodedCursor = java.util.Base64.getUrlEncoder().withoutPadding()
                    .encodeToString(rawCursor.getBytes());
            
            Invoice invoice = createInvoice("INV-001", InvoiceStatus.ISSUED);
            InvoiceRepository.Page page = new InvoiceRepository.Page(List.of(invoice), Optional.empty());
            
            when(invoiceRepository.findByTenant(eq(tenantId), eq(20), any(InvoiceRepository.PageCursor.class)))
                    .thenReturn(page);

            ListInvoicesUseCase.PageResult result = service.list(tenantId, 20, encodedCursor, null);

            assertEquals(1, result.items().size());
        }

        @Test
        @DisplayName("should handle invalid cursor gracefully")
        void shouldHandleInvalidCursor() {
            InvoiceRepository.Page page = new InvoiceRepository.Page(List.of(), Optional.empty());
            
            when(invoiceRepository.findByTenant(eq(tenantId), eq(20), isNull())).thenReturn(page);

            ListInvoicesUseCase.PageResult result = service.list(tenantId, 20, "invalid-cursor", null);

            assertEquals(0, result.items().size());
        }
    }

    @Nested
    @DisplayName("PageResult Record")
    class PageResultRecord {

        @Test
        @DisplayName("should create PageResult with all fields")
        void shouldCreatePageResultWithAllFields() {
            Invoice invoice = createInvoice("INV-001", InvoiceStatus.ISSUED);
            
            ListInvoicesUseCase.PageResult result = new ListInvoicesUseCase.PageResult(
                    List.of(invoice), Optional.of("next-cursor"), 1);
            
            assertEquals(1, result.items().size());
            assertTrue(result.nextCursor().isPresent());
            assertEquals(1, result.total());
        }

        @Test
        @DisplayName("should handle empty PageResult")
        void shouldHandleEmptyPageResult() {
            ListInvoicesUseCase.PageResult result = new ListInvoicesUseCase.PageResult(
                    List.of(), Optional.empty(), 0);
            
            assertTrue(result.items().isEmpty());
            assertFalse(result.nextCursor().isPresent());
            assertEquals(0, result.total());
        }
    }
}
