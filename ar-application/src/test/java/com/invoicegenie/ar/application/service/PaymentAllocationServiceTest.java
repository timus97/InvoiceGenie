package com.invoicegenie.ar.application.service;

import com.invoicegenie.ar.application.port.inbound.PaymentAllocationUseCase;
import com.invoicegenie.ar.domain.model.customer.CustomerId;
import com.invoicegenie.ar.domain.model.invoice.Invoice;
import com.invoicegenie.ar.domain.model.invoice.InvoiceId;
import com.invoicegenie.ar.domain.model.invoice.InvoiceLine;
import com.invoicegenie.ar.domain.model.invoice.InvoiceRepository;
import com.invoicegenie.ar.domain.model.payment.Payment;
import com.invoicegenie.ar.domain.model.payment.PaymentId;
import com.invoicegenie.ar.domain.model.payment.PaymentMethod;
import com.invoicegenie.ar.domain.model.payment.PaymentRepository;
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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@DisplayName("PaymentAllocationService")
@ExtendWith(MockitoExtension.class)
class PaymentAllocationServiceTest {

    @Mock
    private PaymentRepository paymentRepository;

    @Mock
    private InvoiceRepository invoiceRepository;

    private PaymentAllocationService service;
    private TenantId tenantId;
    private CustomerId customerId;
    private PaymentId paymentId;

    @BeforeEach
    void setUp() {
        service = new PaymentAllocationService(paymentRepository, invoiceRepository);
        tenantId = TenantId.of(UUID.randomUUID());
        customerId = CustomerId.of(UUID.randomUUID());
        paymentId = PaymentId.generate();
    }

    private Invoice createInvoice(String number, Money total) {
        Invoice invoice = new Invoice(InvoiceId.generate(), number, "CUST001", total.getCurrencyCode(),
                LocalDate.now(), LocalDate.now().plusDays(30), List.of());
        invoice.addLine(new InvoiceLine(1, "Service", total));
        invoice.issue();
        return invoice;
    }

    private Payment createPayment(Money amount) {
        return new Payment(paymentId, "PAY-001", customerId, amount,
                LocalDate.now(), PaymentMethod.BANK_TRANSFER);
    }

    @Nested
    @DisplayName("Auto Allocate FIFO")
    class AutoAllocateFifo {

        @Test
        @DisplayName("should allocate payment to invoices")
        void shouldAllocatePaymentToInvoices() {
            Payment payment = createPayment(Money.of("500.00", "USD"));
            Invoice invoice = createInvoice("INV-001", Money.of("1000.00", "USD"));

            when(paymentRepository.findByTenantAndId(tenantId, paymentId)).thenReturn(Optional.of(payment));
            when(invoiceRepository.findOpenByTenantAndCustomer(tenantId, customerId)).thenReturn(List.of(invoice));

            Optional<PaymentAllocationUseCase.AllocationResult> result = service.autoAllocateFIFO(
                    tenantId, paymentId, UUID.randomUUID(), null);

            assertTrue(result.isPresent());
            assertEquals(1, result.get().allocations().size());
            verify(paymentRepository).save(eq(tenantId), any());
        }

        @Test
        @DisplayName("should return empty when payment not found")
        void shouldReturnEmptyWhenPaymentNotFound() {
            when(paymentRepository.findByTenantAndId(tenantId, paymentId)).thenReturn(Optional.empty());

            Optional<PaymentAllocationUseCase.AllocationResult> result = service.autoAllocateFIFO(
                    tenantId, paymentId, UUID.randomUUID(), null);

            assertFalse(result.isPresent());
        }

        @Test
        @DisplayName("should use idempotency key for caching")
        void shouldUseIdempotencyKey() {
            Payment payment = createPayment(Money.of("500.00", "USD"));
            Invoice invoice = createInvoice("INV-001", Money.of("1000.00", "USD"));

            when(paymentRepository.findByTenantAndId(tenantId, paymentId)).thenReturn(Optional.of(payment));
            when(invoiceRepository.findOpenByTenantAndCustomer(tenantId, customerId)).thenReturn(List.of(invoice));

            // First call
            Optional<PaymentAllocationUseCase.AllocationResult> result1 = service.autoAllocateFIFO(
                    tenantId, paymentId, UUID.randomUUID(), "idem-key-1");

            // Second call with same key should return cached result
            Optional<PaymentAllocationUseCase.AllocationResult> result2 = service.autoAllocateFIFO(
                    tenantId, paymentId, UUID.randomUUID(), "idem-key-1");

            assertTrue(result1.isPresent());
            assertTrue(result2.isPresent());
            assertEquals(result1.get().paymentId(), result2.get().paymentId());
            // Repository should only be called once due to caching
            verify(paymentRepository, times(1)).save(eq(tenantId), any());
        }
    }

    @Nested
    @DisplayName("Manual Allocate")
    class ManualAllocate {

        @Test
        @DisplayName("should manually allocate to specific invoices")
        void shouldManuallyAllocate() {
            Payment payment = createPayment(Money.of("1000.00", "USD"));
            Invoice invoice1 = createInvoice("INV-001", Money.of("1000.00", "USD"));

            when(paymentRepository.findByTenantAndId(tenantId, paymentId)).thenReturn(Optional.of(payment));
            when(invoiceRepository.findByTenantAndId(eq(tenantId), eq(invoice1.getId()))).thenReturn(Optional.of(invoice1));

            List<PaymentAllocationUseCase.ManualAllocationRequest> requests = List.of(
                    new PaymentAllocationUseCase.ManualAllocationRequest(invoice1.getId(), Money.of("500.00", "USD"), "Payment 1")
            );

            Optional<PaymentAllocationUseCase.AllocationResult> result = service.manualAllocate(
                    tenantId, paymentId, requests, UUID.randomUUID(), null);

            assertTrue(result.isPresent());
        }
    }

    @Nested
    @DisplayName("Get Allocations")
    class GetAllocations {

        @Test
        @DisplayName("should get allocations for payment")
        void shouldGetAllocationsForPayment() {
            Payment payment = createPayment(Money.of("1000.00", "USD"));
            Invoice invoice = createInvoice("INV-001", Money.of("500.00", "USD"));
            payment.allocate(invoice.getId(), Money.of("500.00", "USD"), UUID.randomUUID(), "Test");

            when(paymentRepository.findByTenantAndId(tenantId, paymentId)).thenReturn(Optional.of(payment));

            Optional<PaymentAllocationUseCase.AllocationResult> result = service.getAllocations(tenantId, paymentId);

            assertTrue(result.isPresent());
            assertEquals(1, result.get().allocations().size());
        }

        @Test
        @DisplayName("should return empty when payment not found")
        void shouldReturnEmptyWhenNotFound() {
            when(paymentRepository.findByTenantAndId(tenantId, paymentId)).thenReturn(Optional.empty());

            Optional<PaymentAllocationUseCase.AllocationResult> result = service.getAllocations(tenantId, paymentId);

            assertFalse(result.isPresent());
        }
    }

    @Nested
    @DisplayName("AllocationResult Record")
    class AllocationResultRecord {

        @Test
        @DisplayName("should indicate hasErrors when errors present")
        void shouldIndicateHasErrorsWhenErrorsPresent() {
            PaymentAllocationUseCase.AllocationResult result = new PaymentAllocationUseCase.AllocationResult(
                    paymentId, List.of(), Money.of("0.00", "USD"), Money.of("500.00", "USD"),
                    List.of("Error 1", "Error 2"), 1L);

            assertTrue(result.hasErrors());
            assertEquals(2, result.errors().size());
        }

        @Test
        @DisplayName("should indicate no errors when empty")
        void shouldIndicateNoErrorsWhenEmpty() {
            PaymentAllocationUseCase.AllocationResult result = new PaymentAllocationUseCase.AllocationResult(
                    paymentId, List.of(), Money.of("500.00", "USD"), Money.of("0.00", "USD"),
                    List.of(), 1L);

            assertFalse(result.hasErrors());
        }

        @Test
        @DisplayName("should indicate fully allocated when remaining is zero")
        void shouldIndicateFullyAllocatedWhenZero() {
            PaymentAllocationUseCase.AllocationResult result = new PaymentAllocationUseCase.AllocationResult(
                    paymentId, List.of(), Money.of("500.00", "USD"), Money.of("0.00", "USD"),
                    List.of(), 1L);

            assertTrue(result.isFullyAllocated());
        }

        @Test
        @DisplayName("should indicate not fully allocated when remaining is positive")
        void shouldIndicateNotFullyAllocatedWhenPositive() {
            PaymentAllocationUseCase.AllocationResult result = new PaymentAllocationUseCase.AllocationResult(
                    paymentId, List.of(), Money.of("300.00", "USD"), Money.of("200.00", "USD"),
                    List.of(), 1L);

            assertFalse(result.isFullyAllocated());
        }

        @Test
        @DisplayName("should create allocation detail")
        void shouldCreateAllocationDetail() {
            InvoiceId invoiceId = InvoiceId.generate();
            UUID allocationId = UUID.randomUUID();
            
            PaymentAllocationUseCase.AllocationResult.AllocationDetail detail = 
                    new PaymentAllocationUseCase.AllocationResult.AllocationDetail(
                            invoiceId, Money.of("500.00", "USD"), allocationId);
            
            assertEquals(invoiceId, detail.invoiceId());
            assertEquals(Money.of("500.00", "USD"), detail.amount());
            assertEquals(allocationId, detail.allocationId());
        }
    }

    @Nested
    @DisplayName("ManualAllocationRequest Record")
    class ManualAllocationRequestRecord {

        @Test
        @DisplayName("should create request with all fields")
        void shouldCreateRequestWithAllFields() {
            InvoiceId invoiceId = InvoiceId.generate();
            
            PaymentAllocationUseCase.ManualAllocationRequest request = 
                    new PaymentAllocationUseCase.ManualAllocationRequest(
                            invoiceId, Money.of("500.00", "USD"), "Test notes");
            
            assertEquals(invoiceId, request.invoiceId());
            assertEquals(Money.of("500.00", "USD"), request.amount());
            assertEquals("Test notes", request.notes());
        }

        @Test
        @DisplayName("should create request with null notes")
        void shouldCreateRequestWithNullNotes() {
            InvoiceId invoiceId = InvoiceId.generate();
            
            PaymentAllocationUseCase.ManualAllocationRequest request = 
                    new PaymentAllocationUseCase.ManualAllocationRequest(
                            invoiceId, Money.of("500.00", "USD"), null);
            
            assertEquals(invoiceId, request.invoiceId());
            assertNull(request.notes());
        }
    }
}
