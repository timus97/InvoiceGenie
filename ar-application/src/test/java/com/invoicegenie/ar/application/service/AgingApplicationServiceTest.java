package com.invoicegenie.ar.application.service;

import com.invoicegenie.ar.domain.model.invoice.Invoice;
import com.invoicegenie.ar.domain.model.invoice.InvoiceId;
import com.invoicegenie.ar.domain.model.invoice.InvoiceLine;
import com.invoicegenie.ar.domain.model.invoice.InvoiceRepository;
import com.invoicegenie.ar.domain.model.invoice.InvoiceStatus;
import com.invoicegenie.ar.domain.service.AgingService;
import com.invoicegenie.shared.domain.Money;
import com.invoicegenie.shared.domain.TenantId;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@DisplayName("AgingApplicationService")
@ExtendWith(MockitoExtension.class)
class AgingApplicationServiceTest {

    @Mock private InvoiceRepository invoiceRepository;
    private AgingService agingService;
    private AgingApplicationService service;
    private TenantId tenantId;

    @BeforeEach
    void setUp() {
        agingService = new AgingService();
        service = new AgingApplicationService(agingService, invoiceRepository);
        tenantId = TenantId.of(UUID.randomUUID());
    }

    private Invoice openInvoice(String number, LocalDate dueDate, String amount) {
        InvoiceId id = InvoiceId.of(UUID.randomUUID());
        InvoiceLine line = new InvoiceLine(1, "Item", Money.of(amount, "USD"));
        Invoice invoice = new Invoice(id, number, UUID.randomUUID().toString(), "USD",
                dueDate.minusDays(30), dueDate, List.of(line));
        invoice.issue();
        return invoice;
    }

    @Nested
    @DisplayName("getReport")
    class GetReport {
        @Test
        @DisplayName("should load open invoices and generate report")
        void shouldGenerateReportFromOpenInvoices() {
            Invoice inv = openInvoice("INV-001", LocalDate.now().minusDays(45), "1000.00");
            when(invoiceRepository.findOpenByTenant(tenantId)).thenReturn(List.of(inv));

            AgingService.AgingReportResult result = service.getReport(tenantId, LocalDate.now());

            assertTrue(result.success());
            assertNotNull(result.summary());
            assertEquals(1, result.summary().totalCount());
            assertTrue(result.summary().grandTotal().compareTo(java.math.BigDecimal.ZERO) > 0);
            verify(invoiceRepository).findOpenByTenant(eq(tenantId));
        }

        @Test
        @DisplayName("should return empty report when no open invoices")
        void shouldReturnEmptyReport() {
            when(invoiceRepository.findOpenByTenant(tenantId)).thenReturn(List.of());

            AgingService.AgingReportResult result = service.getReport(tenantId, LocalDate.now());

            assertTrue(result.success());
            assertEquals(0, result.summary().totalCount());
            assertEquals(0, result.summary().grandTotal().compareTo(java.math.BigDecimal.ZERO));
        }

        @Test
        @DisplayName("should default asOfDate to today when null")
        void shouldDefaultAsOfDate() {
            when(invoiceRepository.findOpenByTenant(tenantId)).thenReturn(List.of());

            AgingService.AgingReportResult result = service.getReport(tenantId, null);

            assertTrue(result.success());
            assertEquals(LocalDate.now(), result.summary().asOfDate());
        }

        @Test
        @DisplayName("should bucket overdue invoice correctly")
        void shouldBucketOverdue() {
            Invoice inv = openInvoice("INV-OLD", LocalDate.now().minusDays(100), "500.00");
            when(invoiceRepository.findOpenByTenant(tenantId)).thenReturn(List.of(inv));

            AgingService.AgingReportResult result = service.getReport(tenantId, LocalDate.now());

            assertTrue(result.success());
            assertEquals(1, result.summary().count90Plus());
            assertEquals(0, new java.math.BigDecimal("500.00").compareTo(result.summary().total90Plus()));
        }
    }

    @Nested
    @DisplayName("calculateEarlyPaymentDiscount")
    class Discount {
        @Test
        @DisplayName("should delegate to domain service")
        void shouldDelegate() {
            Money amount = Money.of("1000.00", "USD");
            var result = service.calculateEarlyPaymentDiscount(
                    UUID.randomUUID(), amount, LocalDate.now().plusDays(10), LocalDate.now());

            assertTrue(result.eligible());
            assertEquals(0, new java.math.BigDecimal("20.00").compareTo(result.discountAmount().getAmount()));
        }
    }
}
