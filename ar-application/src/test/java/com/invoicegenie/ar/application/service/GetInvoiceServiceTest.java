package com.invoicegenie.ar.application.service;

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

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@DisplayName("GetInvoiceService")
@ExtendWith(MockitoExtension.class)
class GetInvoiceServiceTest {

    @Mock
    private InvoiceRepository invoiceRepository;

    private GetInvoiceService service;
    private TenantId tenantId;
    private InvoiceId invoiceId;

    @BeforeEach
    void setUp() {
        service = new GetInvoiceService(invoiceRepository);
        tenantId = TenantId.of(UUID.randomUUID());
        invoiceId = InvoiceId.generate();
    }

    @Nested
    @DisplayName("Get Invoice")
    class GetInvoice {

        @Test
        @DisplayName("should return invoice when found")
        void shouldReturnInvoiceWhenFound() {
            Invoice invoice = new Invoice(invoiceId, "INV-001", "CUST001", "USD",
                    LocalDate.now(), LocalDate.now().plusDays(30), List.of());
            invoice.addLine(new InvoiceLine(1, "Service", Money.of("1000.00", "USD")));
            
            when(invoiceRepository.findByTenantAndId(tenantId, invoiceId)).thenReturn(Optional.of(invoice));

            Optional<Invoice> result = service.get(tenantId, invoiceId);

            assertTrue(result.isPresent());
            assertEquals("INV-001", result.get().getInvoiceNumber());
            assertEquals("CUST001", result.get().getCustomerRef());
        }

        @Test
        @DisplayName("should return empty when invoice not found")
        void shouldReturnEmptyWhenNotFound() {
            when(invoiceRepository.findByTenantAndId(tenantId, invoiceId)).thenReturn(Optional.empty());

            Optional<Invoice> result = service.get(tenantId, invoiceId);

            assertFalse(result.isPresent());
        }

        @Test
        @DisplayName("should return issued invoice")
        void shouldReturnIssuedInvoice() {
            Invoice invoice = new Invoice(invoiceId, "INV-001", "CUST001", "USD",
                    LocalDate.now(), LocalDate.now().plusDays(30), List.of());
            invoice.addLine(new InvoiceLine(1, "Service", Money.of("1000.00", "USD")));
            invoice.issue();
            
            when(invoiceRepository.findByTenantAndId(tenantId, invoiceId)).thenReturn(Optional.of(invoice));

            Optional<Invoice> result = service.get(tenantId, invoiceId);

            assertTrue(result.isPresent());
            assertEquals(InvoiceStatus.ISSUED, result.get().getStatus());
        }

        @Test
        @DisplayName("should return paid invoice")
        void shouldReturnPaidInvoice() {
            Invoice invoice = new Invoice(invoiceId, "INV-001", "CUST001", "USD",
                    LocalDate.now(), LocalDate.now().plusDays(30), List.of());
            invoice.addLine(new InvoiceLine(1, "Service", Money.of("1000.00", "USD")));
            invoice.issue();
            invoice.applyPaymentStatus(true);
            
            when(invoiceRepository.findByTenantAndId(tenantId, invoiceId)).thenReturn(Optional.of(invoice));

            Optional<Invoice> result = service.get(tenantId, invoiceId);

            assertTrue(result.isPresent());
            assertEquals(InvoiceStatus.PAID, result.get().getStatus());
        }
    }
}
