package com.invoicegenie.ar.application.service;

import com.invoicegenie.ar.application.port.inbound.ApplyInvoicePaymentUseCase;
import com.invoicegenie.ar.application.port.inbound.PaymentAllocationUseCase;
import com.invoicegenie.ar.application.port.inbound.RecordPaymentUseCase;
import com.invoicegenie.ar.domain.model.customer.CustomerId;
import com.invoicegenie.ar.domain.model.invoice.Invoice;
import com.invoicegenie.ar.domain.model.invoice.InvoiceId;
import com.invoicegenie.ar.domain.model.invoice.InvoiceLine;
import com.invoicegenie.ar.domain.model.invoice.InvoiceRepository;
import com.invoicegenie.ar.domain.model.payment.PaymentId;
import com.invoicegenie.shared.domain.Money;
import com.invoicegenie.shared.domain.TenantId;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@DisplayName("ApplyInvoicePaymentService")
@ExtendWith(MockitoExtension.class)
class ApplyInvoicePaymentServiceTest {

    @Mock private InvoiceRepository invoiceRepository;
    @Mock private RecordPaymentUseCase recordPaymentUseCase;
    @Mock private PaymentAllocationUseCase paymentAllocationUseCase;

    private ApplyInvoicePaymentService service;
    private TenantId tenantId;
    private CustomerId customerId;
    private InvoiceId invoiceId;

    @BeforeEach
    void setUp() {
        service = new ApplyInvoicePaymentService(invoiceRepository, recordPaymentUseCase, paymentAllocationUseCase);
        tenantId = TenantId.of(UUID.randomUUID());
        customerId = CustomerId.of(UUID.randomUUID());
        invoiceId = InvoiceId.generate();
    }

    private Invoice issuedWithCustomer() {
        Invoice invoice = new Invoice(invoiceId, "INV-P1", customerId, customerId.getValue().toString(),
                "USD", LocalDate.now(), LocalDate.now().plusDays(30), List.of());
        invoice.addLine(new InvoiceLine(1, "Svc", Money.of("1000.00", "USD")));
        invoice.issue();
        return invoice;
    }

    private Invoice issuedLegacyCustomerRef() {
        UUID legacy = UUID.randomUUID();
        Invoice invoice = new Invoice(invoiceId, "INV-P2", null, legacy.toString(),
                "USD", LocalDate.now(), LocalDate.now().plusDays(30), List.of());
        invoice.addLine(new InvoiceLine(1, "Svc", Money.of("500.00", "USD")));
        invoice.issue();
        return invoice;
    }

    private PaymentAllocationUseCase.AllocationResult okAlloc(PaymentId pid) {
        return new PaymentAllocationUseCase.AllocationResult(
                pid, List.of(), Money.of("1000.00", "USD"), Money.of("0.00", "USD"), List.of(), 1L);
    }

    @Nested
    @DisplayName("happy path")
    class Happy {
        @Test
        @DisplayName("full pay records payment and allocates")
        void fullPay() {
            Invoice invoice = issuedWithCustomer();
            PaymentId pid = PaymentId.generate();
            when(invoiceRepository.findByTenantAndId(tenantId, invoiceId))
                    .thenReturn(Optional.of(invoice))
                    .thenReturn(Optional.of(invoice));
            when(recordPaymentUseCase.record(eq(tenantId), any())).thenReturn(pid);
            when(paymentAllocationUseCase.manualAllocate(eq(tenantId), eq(pid), anyList(), isNull(), isNull()))
                    .thenReturn(Optional.of(okAlloc(pid)));

            var result = service.apply(tenantId, invoiceId,
                    new ApplyInvoicePaymentUseCase.ApplyPaymentCommand(null, true));

            assertTrue(result.isPresent());
            verify(recordPaymentUseCase).record(eq(tenantId), any());
            verify(paymentAllocationUseCase).manualAllocate(eq(tenantId), eq(pid), anyList(), isNull(), isNull());
        }

        @Test
        @DisplayName("partial amount uses legacy customerRef UUID")
        void partialLegacy() {
            Invoice invoice = issuedLegacyCustomerRef();
            PaymentId pid = PaymentId.generate();
            when(invoiceRepository.findByTenantAndId(tenantId, invoiceId))
                    .thenReturn(Optional.of(invoice))
                    .thenReturn(Optional.of(invoice));
            when(recordPaymentUseCase.record(eq(tenantId), any())).thenReturn(pid);
            when(paymentAllocationUseCase.manualAllocate(eq(tenantId), eq(pid), anyList(), isNull(), isNull()))
                    .thenReturn(Optional.of(new PaymentAllocationUseCase.AllocationResult(
                            pid, List.of(), Money.of("100.00", "USD"), Money.of("0.00", "USD"), List.of(), 1L)));

            var result = service.apply(tenantId, invoiceId,
                    new ApplyInvoicePaymentUseCase.ApplyPaymentCommand(new BigDecimal("100.00"), false));

            assertTrue(result.isPresent());
        }
    }

    @Nested
    @DisplayName("failures")
    class Failures {
        @Test
        @DisplayName("empty when invoice missing")
        void notFound() {
            when(invoiceRepository.findByTenantAndId(tenantId, invoiceId)).thenReturn(Optional.empty());
            assertTrue(service.apply(tenantId, invoiceId,
                    new ApplyInvoicePaymentUseCase.ApplyPaymentCommand(null, true)).isEmpty());
        }

        @Test
        @DisplayName("rejects non-receivable status")
        void badStatus() {
            Invoice invoice = issuedWithCustomer();
            invoice.recordPaymentApplied(invoice.getBalanceDue());
            when(invoiceRepository.findByTenantAndId(tenantId, invoiceId)).thenReturn(Optional.of(invoice));

            assertThrows(IllegalStateException.class, () ->
                    service.apply(tenantId, invoiceId,
                            new ApplyInvoicePaymentUseCase.ApplyPaymentCommand(null, true)));
        }

        @Test
        @DisplayName("requires amount when not fully paid")
        void amountRequired() {
            Invoice invoice = issuedWithCustomer();
            when(invoiceRepository.findByTenantAndId(tenantId, invoiceId)).thenReturn(Optional.of(invoice));

            assertThrows(IllegalArgumentException.class, () ->
                    service.apply(tenantId, invoiceId,
                            new ApplyInvoicePaymentUseCase.ApplyPaymentCommand(null, false)));
        }

        @Test
        @DisplayName("rejects amount exceeding balance")
        void exceedsBalance() {
            Invoice invoice = issuedWithCustomer();
            when(invoiceRepository.findByTenantAndId(tenantId, invoiceId)).thenReturn(Optional.of(invoice));

            assertThrows(IllegalArgumentException.class, () ->
                    service.apply(tenantId, invoiceId,
                            new ApplyInvoicePaymentUseCase.ApplyPaymentCommand(new BigDecimal("9999"), false)));
        }

        @Test
        @DisplayName("rejects non-UUID customerRef without customerId")
        void badCustomerRef() {
            Invoice invoice = new Invoice(invoiceId, "INV-X", null, "NOT-A-UUID",
                    "USD", LocalDate.now(), LocalDate.now().plusDays(30), List.of());
            invoice.addLine(new InvoiceLine(1, "Svc", Money.of("10.00", "USD")));
            invoice.issue();
            when(invoiceRepository.findByTenantAndId(tenantId, invoiceId)).thenReturn(Optional.of(invoice));

            assertThrows(IllegalStateException.class, () ->
                    service.apply(tenantId, invoiceId,
                            new ApplyInvoicePaymentUseCase.ApplyPaymentCommand(null, true)));
        }

        @Test
        @DisplayName("fails when allocation returns errors")
        void allocErrors() {
            Invoice invoice = issuedWithCustomer();
            PaymentId pid = PaymentId.generate();
            when(invoiceRepository.findByTenantAndId(tenantId, invoiceId)).thenReturn(Optional.of(invoice));
            when(recordPaymentUseCase.record(eq(tenantId), any())).thenReturn(pid);
            when(paymentAllocationUseCase.manualAllocate(eq(tenantId), eq(pid), anyList(), isNull(), isNull()))
                    .thenReturn(Optional.of(new PaymentAllocationUseCase.AllocationResult(
                            pid, List.of(), Money.of("0.00", "USD"), Money.of("1000.00", "USD"),
                            List.of("boom"), 1L)));

            assertThrows(IllegalStateException.class, () ->
                    service.apply(tenantId, invoiceId,
                            new ApplyInvoicePaymentUseCase.ApplyPaymentCommand(null, true)));
        }
    }
}