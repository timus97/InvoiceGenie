package com.invoicegenie.ar.domain.service;

import com.invoicegenie.ar.domain.model.customer.CustomerId;
import com.invoicegenie.ar.domain.model.invoice.Invoice;
import com.invoicegenie.ar.domain.model.invoice.InvoiceId;
import com.invoicegenie.ar.domain.model.invoice.InvoiceLine;
import com.invoicegenie.ar.domain.model.payment.Payment;
import com.invoicegenie.ar.domain.model.payment.PaymentAllocation;
import com.invoicegenie.ar.domain.model.payment.PaymentId;
import com.invoicegenie.ar.domain.model.payment.PaymentMethod;
import com.invoicegenie.shared.domain.Money;
import com.invoicegenie.shared.domain.TenantId;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("PaymentAllocationEngine")
class PaymentAllocationEngineTest {

    private PaymentAllocationEngine engine;
    private TenantId tenantId;
    private CustomerId customerId;

    @BeforeEach
    void setUp() {
        engine = new PaymentAllocationEngine();
        tenantId = TenantId.of(UUID.randomUUID());
        customerId = CustomerId.of(UUID.randomUUID());
    }

    private Invoice createInvoice(String number, LocalDate issueDate, LocalDate dueDate, Money total) {
        Invoice invoice = new Invoice(
                InvoiceId.generate(), number, "CUST001", total.getCurrencyCode(),
                issueDate, dueDate, List.of()
        );
        // Add a line to make it issuable
        InvoiceLine line = new InvoiceLine(1, "Service", total);
        invoice.addLine(line);
        invoice.issue();
        return invoice;
    }

    private Payment createPayment(Money amount) {
        return new Payment(
                PaymentId.generate(), "PAY-001", customerId, amount,
                LocalDate.now(), PaymentMethod.BANK_TRANSFER
        );
    }

    @Nested
    @DisplayName("FIFO Auto-Allocation")
    class FifoAutoAllocation {

        @Test
        @DisplayName("should allocate payment to oldest invoice first")
        void shouldAllocateToOldestFirst() {
            Payment payment = createPayment(Money.of("1000.00", "USD"));
            Invoice oldInvoice = createInvoice("INV-001", LocalDate.now().minusDays(30), 
                    LocalDate.now().minusDays(10), Money.of("500.00", "USD"));
            Invoice newInvoice = createInvoice("INV-002", LocalDate.now().minusDays(10),
                    LocalDate.now().plusDays(10), Money.of("500.00", "USD"));
            
            PaymentAllocationEngine.AllocationResult result = engine.autoAllocateFIFO(
                    tenantId, payment, List.of(newInvoice, oldInvoice), UUID.randomUUID());
            
            assertTrue(result.isFullyAllocated());
            assertEquals(2, result.allocations().size());
            assertFalse(result.hasErrors());
        }

        @Test
        @DisplayName("should partially allocate when payment is less than invoice")
        void shouldPartiallyAllocate() {
            Payment payment = createPayment(Money.of("300.00", "USD"));
            Invoice invoice = createInvoice("INV-001", LocalDate.now().minusDays(30),
                    LocalDate.now().plusDays(10), Money.of("1000.00", "USD"));
            
            PaymentAllocationEngine.AllocationResult result = engine.autoAllocateFIFO(
                    tenantId, payment, List.of(invoice), UUID.randomUUID());
            
            // Payment is fully allocated (all 300.00 used), but invoice is partially paid
            assertTrue(result.isFullyAllocated());
            assertEquals(1, result.allocations().size());
            assertEquals(0, new BigDecimal("300.00").compareTo(result.totalAllocated().getAmount()));
            assertEquals(0, new BigDecimal("0.00").compareTo(result.remainingUnallocated().getAmount()));
        }

        @Test
        @DisplayName("should skip invoices with different currency")
        void shouldSkipDifferentCurrency() {
            Payment payment = createPayment(Money.of("1000.00", "USD"));
            Invoice eurInvoice = createInvoice("INV-EUR", LocalDate.now().minusDays(30),
                    LocalDate.now().plusDays(10), Money.of("500.00", "EUR"));
            
            PaymentAllocationEngine.AllocationResult result = engine.autoAllocateFIFO(
                    tenantId, payment, List.of(eurInvoice), UUID.randomUUID());
            
            assertEquals(0, result.allocations().size());
            assertEquals(0, new BigDecimal("1000.00").compareTo(result.remainingUnallocated().getAmount()));
        }

        @Test
        @DisplayName("should return error when payment not RECEIVED")
        void shouldReturnErrorWhenNotReceived() {
            Payment payment = createPayment(Money.of("1000.00", "USD"));
            payment.reverse(); // Change status to REVERSED
            Invoice invoice = createInvoice("INV-001", LocalDate.now().minusDays(30),
                    LocalDate.now().plusDays(10), Money.of("500.00", "USD"));
            
            PaymentAllocationEngine.AllocationResult result = engine.autoAllocateFIFO(
                    tenantId, payment, List.of(invoice), UUID.randomUUID());
            
            assertTrue(result.hasErrors());
            assertTrue(result.errors().get(0).reason().contains("RECEIVED"));
        }
    }

    @Nested
    @DisplayName("Manual Allocation")
    class ManualAllocation {

        @Test
        @DisplayName("should allocate to specified invoices")
        void shouldAllocateToSpecifiedInvoices() {
            Payment payment = createPayment(Money.of("1000.00", "USD"));
            Invoice invoice1 = createInvoice("INV-001", LocalDate.now().minusDays(30),
                    LocalDate.now().plusDays(10), Money.of("500.00", "USD"));
            Invoice invoice2 = createInvoice("INV-002", LocalDate.now().minusDays(20),
                    LocalDate.now().plusDays(20), Money.of("500.00", "USD"));
            
            List<PaymentAllocationEngine.ManualAllocationRequest> requests = List.of(
                    new PaymentAllocationEngine.ManualAllocationRequest(
                            invoice1.getId(), Money.of("400.00", "USD"), "Partial payment"),
                    new PaymentAllocationEngine.ManualAllocationRequest(
                            invoice2.getId(), Money.of("600.00", "USD"), "Full payment")
            );
            
            PaymentAllocationEngine.AllocationResult result = engine.manualAllocate(
                    tenantId, payment, requests, UUID.randomUUID());
            
            assertTrue(result.isFullyAllocated());
            assertEquals(2, result.allocations().size());
        }

        @Test
        @DisplayName("should return error when allocation exceeds unallocated")
        void shouldReturnErrorWhenExceeds() {
            Payment payment = createPayment(Money.of("500.00", "USD"));
            Invoice invoice = createInvoice("INV-001", LocalDate.now().minusDays(30),
                    LocalDate.now().plusDays(10), Money.of("1000.00", "USD"));
            
            List<PaymentAllocationEngine.ManualAllocationRequest> requests = List.of(
                    new PaymentAllocationEngine.ManualAllocationRequest(
                            invoice.getId(), Money.of("600.00", "USD"), "Over payment")
            );
            
            PaymentAllocationEngine.AllocationResult result = engine.manualAllocate(
                    tenantId, payment, requests, UUID.randomUUID());
            
            assertTrue(result.hasErrors());
        }

        @Test
        @DisplayName("should return error when payment not RECEIVED")
        void shouldReturnErrorWhenNotReceived() {
            Payment payment = createPayment(Money.of("1000.00", "USD"));
            payment.refund();
            Invoice invoice = createInvoice("INV-001", LocalDate.now().minusDays(30),
                    LocalDate.now().plusDays(10), Money.of("500.00", "USD"));
            
            List<PaymentAllocationEngine.ManualAllocationRequest> requests = List.of(
                    new PaymentAllocationEngine.ManualAllocationRequest(
                            invoice.getId(), Money.of("500.00", "USD"), "Payment")
            );
            
            PaymentAllocationEngine.AllocationResult result = engine.manualAllocate(
                    tenantId, payment, requests, UUID.randomUUID());
            
            assertTrue(result.hasErrors());
        }
    }

    @Nested
    @DisplayName("Calculate Outstanding")
    class CalculateOutstanding {

        @Test
        @DisplayName("should calculate outstanding balance")
        void shouldCalculateOutstanding() {
            Money invoiceTotal = Money.of("1000.00", "USD");
            
            PaymentAllocation alloc1 = new PaymentAllocation(
                    UUID.randomUUID(), InvoiceId.generate(), Money.of("300.00", "USD"),
                    UUID.randomUUID(), "Payment 1");
            PaymentAllocation alloc2 = new PaymentAllocation(
                    UUID.randomUUID(), InvoiceId.generate(), Money.of("200.00", "USD"),
                    UUID.randomUUID(), "Payment 2");
            
            Money outstanding = engine.calculateOutstanding(invoiceTotal, List.of(alloc1, alloc2));
            
            assertEquals(0, new BigDecimal("500.00").compareTo(outstanding.getAmount()));
        }

        @Test
        @DisplayName("should return full amount when no allocations")
        void shouldReturnFullWhenNoAllocations() {
            Money invoiceTotal = Money.of("1000.00", "USD");
            
            Money outstanding = engine.calculateOutstanding(invoiceTotal, List.of());
            
            assertEquals(invoiceTotal, outstanding);
        }
    }
}
