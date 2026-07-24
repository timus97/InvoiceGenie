package com.invoicegenie.ar.application.service;

import com.invoicegenie.ar.domain.model.customer.CustomerId;
import com.invoicegenie.ar.domain.model.invoice.Invoice;
import com.invoicegenie.ar.domain.model.invoice.InvoiceId;
import com.invoicegenie.ar.domain.model.invoice.InvoiceLine;
import com.invoicegenie.ar.domain.model.invoice.InvoiceRepository;
import com.invoicegenie.ar.domain.model.payment.PaymentAllocation;
import com.invoicegenie.ar.domain.model.payment.PaymentRepository;
import com.invoicegenie.shared.domain.Money;
import com.invoicegenie.shared.domain.TenantId;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@DisplayName("AllocationIntegrityService")
@ExtendWith(MockitoExtension.class)
class AllocationIntegrityServiceTest {

    @Mock private InvoiceRepository invoiceRepository;
    @Mock private PaymentRepository paymentRepository;

    private AllocationIntegrityService service;
    private TenantId tenantId;

    @BeforeEach
    void setUp() {
        service = new AllocationIntegrityService(invoiceRepository, paymentRepository);
        tenantId = TenantId.of(UUID.randomUUID());
    }

    @Test
    @DisplayName("detects amountPaid vs allocation sum mismatch")
    void detectsMismatch() {
        CustomerId customerId = CustomerId.of(UUID.randomUUID());
        Invoice inv = new Invoice(InvoiceId.generate(), "INV-M1", customerId, "C",
                "USD", LocalDate.now(), LocalDate.now().plusDays(30), List.of());
        inv.addLine(new InvoiceLine(1, "Svc", Money.of("1000.00", "USD")));
        inv.issue();
        inv.recordPaymentApplied(Money.of("500.00", "USD"));

        when(invoiceRepository.findOpenByTenant(tenantId)).thenReturn(List.of(inv));
        when(paymentRepository.findAllocationsByTenantAndInvoice(tenantId, inv.getId()))
                .thenReturn(List.of(new PaymentAllocation(
                        UUID.randomUUID(), inv.getId(), Money.of("300.00", "USD"),
                        UUID.randomUUID(), null)));

        var mismatches = service.reconcileOpenInvoices(tenantId);
        assertEquals(1, mismatches.size());
        assertEquals(0, mismatches.get(0).amountPaid().compareTo(new java.math.BigDecimal("500.00")));
        assertEquals(0, mismatches.get(0).allocationsSum().compareTo(new java.math.BigDecimal("300.00")));
    }

    @Test
    @DisplayName("returns empty when balanced")
    void balanced() {
        CustomerId customerId = CustomerId.of(UUID.randomUUID());
        Invoice inv = new Invoice(InvoiceId.generate(), "INV-M2", customerId, "C",
                "USD", LocalDate.now(), LocalDate.now().plusDays(30), List.of());
        inv.addLine(new InvoiceLine(1, "Svc", Money.of("100.00", "USD")));
        inv.issue();
        inv.recordPaymentApplied(Money.of("100.00", "USD"));

        when(invoiceRepository.findOpenByTenant(tenantId)).thenReturn(List.of(inv));
        when(paymentRepository.findAllocationsByTenantAndInvoice(tenantId, inv.getId()))
                .thenReturn(List.of(new PaymentAllocation(
                        UUID.randomUUID(), inv.getId(), Money.of("100.00", "USD"),
                        UUID.randomUUID(), null)));

        assertTrue(service.reconcileOpenInvoices(tenantId).isEmpty());
    }
}