package com.invoicegenie.ar.application.service;

import com.invoicegenie.ar.application.port.inbound.PaymentAllocationUseCase;
import com.invoicegenie.ar.application.port.outbound.EventPublisher;
import com.invoicegenie.ar.application.port.outbound.IdempotencyStore;
import com.invoicegenie.ar.domain.event.PaymentAllocated;
import com.invoicegenie.ar.domain.model.customer.CustomerId;
import com.invoicegenie.ar.domain.model.invoice.Invoice;
import com.invoicegenie.ar.domain.model.invoice.InvoiceId;
import com.invoicegenie.ar.domain.model.invoice.InvoiceLine;
import com.invoicegenie.ar.domain.model.invoice.InvoiceRepository;
import com.invoicegenie.ar.domain.model.invoice.InvoiceStatus;
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
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@DisplayName("PaymentAllocationService")
@ExtendWith(MockitoExtension.class)
class PaymentAllocationServiceTest {

    @Mock private PaymentRepository paymentRepository;
    @Mock private InvoiceRepository invoiceRepository;
    @Mock private EventPublisher eventPublisher;
    @Mock private IdempotencyStore idempotencyStore;

    private PaymentAllocationService service;
    private TenantId tenantId;
    private CustomerId customerId;
    private PaymentId paymentId;

    @BeforeEach
    void setUp() {
        service = new PaymentAllocationService(paymentRepository, invoiceRepository, eventPublisher, idempotencyStore);
        tenantId = TenantId.of(UUID.randomUUID());
        customerId = CustomerId.of(UUID.randomUUID());
        paymentId = PaymentId.generate();
    }

    private Invoice createInvoice(String number, Money total) {
        Invoice invoice = new Invoice(InvoiceId.generate(), number, null, "CUST001", total.getCurrencyCode(),
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
            assertEquals(InvoiceStatus.PARTIALLY_PAID, invoice.getStatus());
            verify(paymentRepository).save(eq(tenantId), any());
            verify(eventPublisher).publish(any(PaymentAllocated.class));
        }

        @Test
        @DisplayName("should not over-allocate across multiple payments")
        void shouldNotOverAllocateAcrossMultiplePayments() {
            Invoice invoice = createInvoice("INV-001", Money.of("1000.00", "USD"));
            invoice.recordPaymentApplied(Money.of("700.00", "USD"));
            Payment payment2 = createPayment(Money.of("500.00", "USD"));
            when(paymentRepository.findByTenantAndId(tenantId, paymentId)).thenReturn(Optional.of(payment2));
            when(invoiceRepository.findOpenByTenantAndCustomer(tenantId, customerId)).thenReturn(List.of(invoice));

            Optional<PaymentAllocationUseCase.AllocationResult> result = service.autoAllocateFIFO(
                    tenantId, paymentId, UUID.randomUUID(), null);

            assertTrue(result.isPresent());
            assertEquals(Money.of("300.00", "USD"), result.get().allocations().get(0).amount());
            assertEquals(InvoiceStatus.PAID, invoice.getStatus());
            assertEquals(Money.of("200.00", "USD"), result.get().remainingUnallocated());
        }

        @Test
        @DisplayName("should return empty when payment not found")
        void shouldReturnEmptyWhenPaymentNotFound() {
            when(paymentRepository.findByTenantAndId(tenantId, paymentId)).thenReturn(Optional.empty());
            assertFalse(service.autoAllocateFIFO(tenantId, paymentId, UUID.randomUUID(), null).isPresent());
            verifyNoInteractions(eventPublisher);
        }

        @Test
        @DisplayName("should use durable idempotency store for caching")
        void shouldUseIdempotencyKey() {
            Payment payment = createPayment(Money.of("500.00", "USD"));
            Invoice invoice = createInvoice("INV-001", Money.of("1000.00", "USD"));
            when(paymentRepository.findByTenantAndId(tenantId, paymentId)).thenReturn(Optional.of(payment));
            when(invoiceRepository.findOpenByTenantAndCustomer(tenantId, customerId)).thenReturn(List.of(invoice));
            when(idempotencyStore.find(eq(tenantId), anyString())).thenReturn(Optional.empty());

            Optional<PaymentAllocationUseCase.AllocationResult> result1 = service.autoAllocateFIFO(
                    tenantId, paymentId, UUID.randomUUID(), "idem-key-1");

            ArgumentCaptor<String> keyCaptor = ArgumentCaptor.forClass(String.class);
            ArgumentCaptor<String> hashCaptor = ArgumentCaptor.forClass(String.class);
            ArgumentCaptor<String> payloadCaptor = ArgumentCaptor.forClass(String.class);
            verify(idempotencyStore).put(eq(tenantId), keyCaptor.capture(), hashCaptor.capture(), payloadCaptor.capture());

            when(idempotencyStore.find(eq(tenantId), eq(keyCaptor.getValue())))
                    .thenReturn(Optional.of(new IdempotencyStore.IdempotencyRecord(
                            keyCaptor.getValue(), hashCaptor.getValue(), payloadCaptor.getValue(),
                            java.time.Instant.now())));

            Optional<PaymentAllocationUseCase.AllocationResult> result2 = service.autoAllocateFIFO(
                    tenantId, paymentId, UUID.randomUUID(), "idem-key-1");

            assertTrue(result1.isPresent());
            assertTrue(result2.isPresent());
            assertEquals(result1.get().paymentId(), result2.get().paymentId());
            verify(paymentRepository, times(1)).save(eq(tenantId), any());
            verify(eventPublisher, times(1)).publish(any(PaymentAllocated.class));
        }

        @Test
        @DisplayName("should not publish when no allocations made")
        void shouldNotPublishWhenNoAllocations() {
            Payment payment = createPayment(Money.of("500.00", "USD"));
            when(paymentRepository.findByTenantAndId(tenantId, paymentId)).thenReturn(Optional.of(payment));
            when(invoiceRepository.findOpenByTenantAndCustomer(tenantId, customerId)).thenReturn(List.of());

            Optional<PaymentAllocationUseCase.AllocationResult> result = service.autoAllocateFIFO(
                    tenantId, paymentId, UUID.randomUUID(), null);

            assertTrue(result.isPresent());
            assertEquals(0, result.get().allocations().size());
            verify(paymentRepository, never()).save(any(), any());
            verifyNoInteractions(eventPublisher);
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
                    new PaymentAllocationUseCase.ManualAllocationRequest(
                            invoice1.getId(), Money.of("500.00", "USD"), "Payment 1"));

            Optional<PaymentAllocationUseCase.AllocationResult> result = service.manualAllocate(
                    tenantId, paymentId, requests, UUID.randomUUID(), null);

            assertTrue(result.isPresent());
            assertEquals(1, result.get().allocations().size());
            assertEquals(InvoiceStatus.PARTIALLY_PAID, invoice1.getStatus());
            verify(eventPublisher).publish(any(PaymentAllocated.class));
        }

        @Test
        @DisplayName("should reject manual allocation that exceeds remaining balance")
        void shouldRejectManualOverAllocation() {
            Payment payment = createPayment(Money.of("1000.00", "USD"));
            Invoice invoice = createInvoice("INV-001", Money.of("1000.00", "USD"));
            invoice.recordPaymentApplied(Money.of("800.00", "USD"));
            when(paymentRepository.findByTenantAndId(tenantId, paymentId)).thenReturn(Optional.of(payment));
            when(invoiceRepository.findByTenantAndId(eq(tenantId), eq(invoice.getId()))).thenReturn(Optional.of(invoice));

            List<PaymentAllocationUseCase.ManualAllocationRequest> requests = List.of(
                    new PaymentAllocationUseCase.ManualAllocationRequest(
                            invoice.getId(), Money.of("300.00", "USD"), "Too much"));

            Optional<PaymentAllocationUseCase.AllocationResult> result = service.manualAllocate(
                    tenantId, paymentId, requests, UUID.randomUUID(), null);

            assertTrue(result.isPresent());
            assertTrue(result.get().hasErrors());
            assertEquals(0, result.get().allocations().size());
            verify(paymentRepository, never()).save(any(), any());
            verifyNoInteractions(eventPublisher);
        }

        @Test
        @DisplayName("should use payment currency for allocation amounts")
        void shouldUsePaymentCurrency() {
            Payment payment = createPayment(Money.of("1000.00", "EUR"));
            Invoice invoice1 = createInvoice("INV-001", Money.of("1000.00", "EUR"));
            when(paymentRepository.findByTenantAndId(tenantId, paymentId)).thenReturn(Optional.of(payment));
            when(invoiceRepository.findByTenantAndId(eq(tenantId), eq(invoice1.getId()))).thenReturn(Optional.of(invoice1));

            List<PaymentAllocationUseCase.ManualAllocationRequest> requests = List.of(
                    new PaymentAllocationUseCase.ManualAllocationRequest(
                            invoice1.getId(), Money.of("500.00", "USD"), "Payment 1"));

            Optional<PaymentAllocationUseCase.AllocationResult> result = service.manualAllocate(
                    tenantId, paymentId, requests, UUID.randomUUID(), null);

            assertTrue(result.isPresent());
            assertEquals("EUR", result.get().allocations().get(0).amount().getCurrencyCode());
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
            assertFalse(service.getAllocations(tenantId, paymentId).isPresent());
        }
    }

    @Nested
    @DisplayName("AllocationResult Record")
    class AllocationResultRecord {

        @Test
        void shouldIndicateHasErrorsWhenErrorsPresent() {
            var result = new PaymentAllocationUseCase.AllocationResult(
                    paymentId, List.of(), Money.of("0.00", "USD"), Money.of("500.00", "USD"),
                    List.of("Error 1", "Error 2"), 1L);
            assertTrue(result.hasErrors());
        }

        @Test
        void shouldIndicateNoErrorsWhenEmpty() {
            var result = new PaymentAllocationUseCase.AllocationResult(
                    paymentId, List.of(), Money.of("500.00", "USD"), Money.of("0.00", "USD"), List.of(), 1L);
            assertFalse(result.hasErrors());
        }

        @Test
        void shouldIndicateFullyAllocatedWhenZero() {
            var result = new PaymentAllocationUseCase.AllocationResult(
                    paymentId, List.of(), Money.of("500.00", "USD"), Money.of("0.00", "USD"), List.of(), 1L);
            assertTrue(result.isFullyAllocated());
        }

        @Test
        void shouldIndicateNotFullyAllocatedWhenPositive() {
            var result = new PaymentAllocationUseCase.AllocationResult(
                    paymentId, List.of(), Money.of("300.00", "USD"), Money.of("200.00", "USD"), List.of(), 1L);
            assertFalse(result.isFullyAllocated());
        }

        @Test
        void shouldCreateAllocationDetail() {
            InvoiceId invoiceId = InvoiceId.generate();
            UUID allocationId = UUID.randomUUID();
            var detail = new PaymentAllocationUseCase.AllocationResult.AllocationDetail(
                    invoiceId, Money.of("500.00", "USD"), allocationId);
            assertEquals(invoiceId, detail.invoiceId());
            assertEquals(allocationId, detail.allocationId());
        }

        @Test
        void shouldRoundTripSerialize() {
            InvoiceId invoiceId = InvoiceId.generate();
            UUID allocationId = UUID.randomUUID();
            var original = new PaymentAllocationUseCase.AllocationResult(
                    paymentId,
                    List.of(new PaymentAllocationUseCase.AllocationResult.AllocationDetail(
                            invoiceId, Money.of("250.00", "USD"), allocationId)),
                    Money.of("250.00", "USD"), Money.of("750.00", "USD"), List.of(), 2L);
            String payload = PaymentAllocationService.serialize(original);
            var restored = PaymentAllocationService.deserialize(payload);
            assertEquals(original.paymentId(), restored.paymentId());
            assertEquals(original.totalAllocated(), restored.totalAllocated());
            assertEquals(1, restored.allocations().size());
            assertEquals(allocationId, restored.allocations().get(0).allocationId());
        }
    }

    @Nested
    @DisplayName("ManualAllocationRequest Record")
    class ManualAllocationRequestRecord {

        @Test
        void shouldCreateRequestWithAllFields() {
            InvoiceId invoiceId = InvoiceId.generate();
            var request = new PaymentAllocationUseCase.ManualAllocationRequest(
                    invoiceId, Money.of("500.00", "USD"), "Test notes");
            assertEquals(invoiceId, request.invoiceId());
            assertEquals("Test notes", request.notes());
        }

        @Test
        void shouldCreateRequestWithNullNotes() {
            InvoiceId invoiceId = InvoiceId.generate();
            var request = new PaymentAllocationUseCase.ManualAllocationRequest(
                    invoiceId, Money.of("500.00", "USD"), null);
            assertNull(request.notes());
        }
    }
}
