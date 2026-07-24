package com.invoicegenie.ar.application.service;

import com.invoicegenie.ar.application.port.inbound.PaymentUnallocateUseCase;
import com.invoicegenie.ar.domain.exception.ConcurrencyConflictException;
import com.invoicegenie.ar.domain.model.customer.CustomerId;
import com.invoicegenie.ar.domain.model.invoice.Invoice;
import com.invoicegenie.ar.domain.model.invoice.InvoiceId;
import com.invoicegenie.ar.domain.model.invoice.InvoiceLine;
import com.invoicegenie.ar.domain.model.invoice.InvoiceRepository;
import com.invoicegenie.ar.domain.model.invoice.InvoiceStatus;
import com.invoicegenie.ar.domain.model.outbox.AuditEntry;
import com.invoicegenie.ar.domain.model.outbox.AuditRepository;
import com.invoicegenie.ar.domain.model.payment.Payment;
import com.invoicegenie.ar.domain.model.payment.PaymentId;
import com.invoicegenie.ar.domain.model.payment.PaymentMethod;
import com.invoicegenie.ar.domain.model.payment.PaymentRepository;
import com.invoicegenie.ar.domain.model.payment.PaymentStatus;
import com.invoicegenie.shared.domain.Money;
import com.invoicegenie.shared.domain.TenantId;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
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

@DisplayName("PaymentUnallocateService")
@ExtendWith(MockitoExtension.class)
class PaymentUnallocateServiceTest {

    @Mock private PaymentRepository paymentRepository;
    @Mock private InvoiceRepository invoiceRepository;
    @Mock private AuditRepository auditRepository;

    private PaymentUnallocateService service;
    private TenantId tenantId;
    private CustomerId customerId;
    private PaymentId paymentId;

    @BeforeEach
    void setUp() {
        service = new PaymentUnallocateService(paymentRepository, invoiceRepository, auditRepository);
        tenantId = TenantId.of(UUID.randomUUID());
        customerId = CustomerId.of(UUID.randomUUID());
        paymentId = PaymentId.generate();
    }

    private Invoice issuedInvoice(String number, Money total) {
        Invoice invoice = new Invoice(InvoiceId.generate(), number, customerId, "CUST",
                total.getCurrencyCode(), LocalDate.now(), LocalDate.now().plusDays(30), List.of());
        invoice.addLine(new InvoiceLine(1, "Svc", total));
        invoice.issue();
        return invoice;
    }

    @Test
    @DisplayName("unallocates specific invoice and leaves payment RECEIVED")
    void unallocateKeepsReceived() {
        Payment payment = new Payment(paymentId, "PAY-U1", customerId, Money.of("1000.00", "USD"),
                LocalDate.now(), PaymentMethod.BANK_TRANSFER);
        Invoice invoice = issuedInvoice("INV-U1", Money.of("1000.00", "USD"));
        payment.allocate(invoice.getId(), Money.of("1000.00", "USD"), UUID.randomUUID(), null);
        invoice.recordPaymentApplied(Money.of("1000.00", "USD"));

        // Reconstitute payment so originalVersion reflects post-allocate version
        Payment loaded = new Payment(
                payment.getId(), payment.getPaymentNumber(), payment.getCustomerId(), payment.getAmount(),
                payment.getPaymentDate(), payment.getReceivedAt(), payment.getMethod(),
                payment.getReference(), payment.getBankAccountId(), payment.getNotes(),
                payment.getStatus(), payment.getCreatedAt(), payment.getUpdatedAt(), payment.getVersion(),
                payment.getAllocations());
        Invoice loadedInv = reconstitute(invoice);

        when(paymentRepository.findByTenantAndId(tenantId, paymentId)).thenReturn(Optional.of(loaded));
        when(invoiceRepository.findByTenantAndId(tenantId, invoice.getId())).thenReturn(Optional.of(loadedInv));

        Optional<PaymentUnallocateUseCase.UnallocateResult> result = service.unallocate(
                tenantId, paymentId, List.of(invoice.getId().getValue()), "misapplied", null);

        assertTrue(result.isPresent());
        assertEquals(PaymentStatus.RECEIVED.name(), result.get().paymentStatus());
        assertEquals(1, result.get().unallocatedInvoiceIds().size());
        assertEquals(0, loaded.getAllocations().size());
        assertEquals(0, Money.of("1000.00", "USD").getAmount().compareTo(loaded.getAmountUnallocated().getAmount()));
        assertEquals(InvoiceStatus.ISSUED, loadedInv.getStatus());
        assertEquals(0, loadedInv.getAmountPaid().getAmount().signum());
        verify(paymentRepository).save(eq(tenantId), eq(loaded));
        verify(invoiceRepository).save(eq(tenantId), eq(loadedInv));
        ArgumentCaptor<AuditEntry> audit = ArgumentCaptor.forClass(AuditEntry.class);
        verify(auditRepository).save(eq(tenantId), audit.capture());
        assertEquals("UNALLOCATE", audit.getValue().getAction());
    }

    @Test
    @DisplayName("rejects stale expectedVersion with ConcurrencyConflictException")
    void versionConflict() {
        Payment payment = new Payment(paymentId, "PAY-U2", customerId, Money.of("100.00", "USD"),
                LocalDate.now(), PaymentMethod.CASH);
        Invoice invoice = issuedInvoice("INV-U2", Money.of("100.00", "USD"));
        payment.allocate(invoice.getId(), Money.of("100.00", "USD"), UUID.randomUUID(), null);
        Payment loaded = new Payment(
                payment.getId(), payment.getPaymentNumber(), payment.getCustomerId(), payment.getAmount(),
                payment.getPaymentDate(), payment.getReceivedAt(), payment.getMethod(),
                null, null, null, payment.getStatus(), payment.getCreatedAt(), payment.getUpdatedAt(),
                payment.getVersion(), payment.getAllocations());
        when(paymentRepository.findByTenantAndId(tenantId, paymentId)).thenReturn(Optional.of(loaded));

        assertThrows(ConcurrencyConflictException.class, () ->
                service.unallocate(tenantId, paymentId, List.of(invoice.getId().getValue()),
                        "retry", loaded.getOriginalVersion() - 1));
    }

    @Test
    @DisplayName("returns empty when payment missing")
    void notFound() {
        when(paymentRepository.findByTenantAndId(any(), any())).thenReturn(Optional.empty());
        assertTrue(service.unallocate(tenantId, paymentId, List.of(UUID.randomUUID()), "x", null).isEmpty());
    }

    private Invoice reconstitute(Invoice inv) {
        return new Invoice(
                inv.getId(), inv.getInvoiceNumber(), inv.getCustomerId(), inv.getCustomerRef(),
                inv.getCurrencyCode(), inv.getIssueDate(), inv.getDueDate(), inv.getPeriodStart(),
                inv.getPeriodEnd(), inv.getCreatedAt(), inv.getUpdatedAt(), inv.getVersion(),
                inv.getNotes(), inv.getTerms(), inv.getStatus(), inv.getIssuedAt(), inv.getWrittenOffAt(),
                inv.getAmountPaid(), inv.getLines());
    }
}