package com.invoicegenie.ar.application.service;

import com.invoicegenie.ar.application.port.outbound.IdempotencyStore;
import com.invoicegenie.ar.domain.exception.IdempotencyConflictException;
import com.invoicegenie.ar.domain.model.customer.CustomerId;
import com.invoicegenie.ar.domain.model.invoice.Invoice;
import com.invoicegenie.ar.domain.model.invoice.InvoiceId;
import com.invoicegenie.ar.domain.model.invoice.InvoiceLine;
import com.invoicegenie.ar.domain.model.invoice.InvoiceRepository;
import com.invoicegenie.ar.domain.model.ledger.LedgerRepository;
import com.invoicegenie.ar.domain.model.outbox.AuditRepository;
import com.invoicegenie.ar.domain.model.payment.Payment;
import com.invoicegenie.ar.domain.model.payment.PaymentId;
import com.invoicegenie.ar.domain.model.payment.PaymentMethod;
import com.invoicegenie.ar.domain.model.payment.PaymentRepository;
import com.invoicegenie.ar.domain.model.payment.PaymentStatus;
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
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@DisplayName("PaymentReversalService")
@ExtendWith(MockitoExtension.class)
class PaymentReversalServiceTest {

    @Mock private PaymentRepository paymentRepository;
    @Mock private InvoiceRepository invoiceRepository;
    @Mock private LedgerRepository ledgerRepository;
    @Mock private AuditRepository auditRepository;
    @Mock private IdempotencyStore idempotencyStore;

    private PaymentReversalService service;
    private TenantId tenantId;
    private CustomerId customerId;
    private PaymentId paymentId;

    @BeforeEach
    void setUp() {
        service = new PaymentReversalService(paymentRepository, invoiceRepository, new LedgerService(),
                ledgerRepository, auditRepository, idempotencyStore);
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

    private Payment receivedPayment(Money amount) {
        return new Payment(paymentId, "PAY-R1", customerId, amount, LocalDate.now(), PaymentMethod.BANK_TRANSFER);
    }

    @Nested
    @DisplayName("reverse")
    class Reverse {

        @Test
        @DisplayName("happy path unwinds allocation and posts ledger")
        void happyPath() {
            Payment payment = receivedPayment(Money.of("1000.00", "USD"));
            Invoice invoice = issuedInvoice("INV-R1", Money.of("1000.00", "USD"));
            payment.allocate(invoice.getId(), Money.of("1000.00", "USD"), UUID.randomUUID(), null);
            invoice.recordPaymentApplied(Money.of("1000.00", "USD"));

            when(paymentRepository.findByTenantAndId(tenantId, paymentId)).thenReturn(Optional.of(payment));
            when(invoiceRepository.findByTenantAndId(tenantId, invoice.getId())).thenReturn(Optional.of(invoice));
            when(idempotencyStore.find(any(), any())).thenReturn(Optional.empty());

            var result = service.reverse(tenantId, paymentId, "duplicate", "rev-key-1");

            assertTrue(result.isPresent());
            assertEquals(PaymentStatus.REVERSED.name(), result.get().newStatus());
            assertEquals(1, result.get().affectedInvoiceIds().size());
            assertEquals(invoice.getId().getValue(), result.get().affectedInvoiceIds().get(0));
            verify(paymentRepository).save(eq(tenantId), eq(payment));
            verify(invoiceRepository).save(eq(tenantId), eq(invoice));
            verify(ledgerRepository).saveAll(eq(tenantId), anyList());
            verify(auditRepository).save(eq(tenantId), any());
            verify(idempotencyStore).put(eq(tenantId),
                    eq("payment-reverse:" + paymentId.getValue() + ":rev-key-1"),
                    anyString(), eq(PaymentStatus.REVERSED.name()));
        }

        @Test
        @DisplayName("returns empty when payment not found")
        void notFound() {
            when(paymentRepository.findByTenantAndId(tenantId, paymentId)).thenReturn(Optional.empty());

            assertTrue(service.reverse(tenantId, paymentId, "x", null).isEmpty());
            verifyNoInteractions(ledgerRepository, auditRepository);
        }

        @Test
        @DisplayName("rejects non-RECEIVED payment")
        void wrongStatus() {
            Payment payment = receivedPayment(Money.of("100.00", "USD"));
            payment.reverse();
            when(paymentRepository.findByTenantAndId(tenantId, paymentId)).thenReturn(Optional.of(payment));

            var ex = assertThrows(IllegalStateException.class,
                    () -> service.reverse(tenantId, paymentId, "again", null));
            assertTrue(ex.getMessage().contains("Only RECEIVED"));
        }

        @Test
        @DisplayName("idempotent replay returns cached status")
        void idempotentReplay() {
            when(idempotencyStore.find(eq(tenantId),
                    eq("payment-reverse:" + paymentId.getValue() + ":same-key")))
                    .thenReturn(Optional.empty());

            Payment payment = receivedPayment(Money.of("50.00", "USD"));
            when(paymentRepository.findByTenantAndId(tenantId, paymentId)).thenReturn(Optional.of(payment));

            service.reverse(tenantId, paymentId, "reason-a", "same-key");

            ArgumentCaptor<String> hashCaptor = ArgumentCaptor.forClass(String.class);
            verify(idempotencyStore).put(eq(tenantId),
                    eq("payment-reverse:" + paymentId.getValue() + ":same-key"),
                    hashCaptor.capture(), anyString());
            String hash = hashCaptor.getValue();

            when(idempotencyStore.find(eq(tenantId),
                    eq("payment-reverse:" + paymentId.getValue() + ":same-key")))
                    .thenReturn(Optional.of(new IdempotencyStore.IdempotencyRecord(
                            "payment-reverse:" + paymentId.getValue() + ":same-key",
                            hash, PaymentStatus.REVERSED.name(), java.time.Instant.now())));

            var second = service.reverse(tenantId, paymentId, "reason-a", "same-key");
            assertTrue(second.isPresent());
            assertEquals(PaymentStatus.REVERSED.name(), second.get().newStatus());
            assertEquals("Idempotent replay", second.get().message());
            verify(paymentRepository, times(1)).save(any(), any());
        }

        @Test
        @DisplayName("idempotency key conflict throws")
        void idempotencyConflict() {
            when(idempotencyStore.find(eq(tenantId),
                    eq("payment-reverse:" + paymentId.getValue() + ":k")))
                    .thenReturn(Optional.of(new IdempotencyStore.IdempotencyRecord(
                            "k", "different-hash", "REVERSED", java.time.Instant.now())));

            assertThrows(IdempotencyConflictException.class,
                    () -> service.reverse(tenantId, paymentId, "other-reason", "k"));
        }
    }

    @Nested
    @DisplayName("refund")
    class Refund {

        @Test
        @DisplayName("happy path sets REFUNDED")
        void happyPath() {
            Payment payment = receivedPayment(Money.of("200.00", "USD"));
            when(paymentRepository.findByTenantAndId(tenantId, paymentId)).thenReturn(Optional.of(payment));

            var result = service.refund(tenantId, paymentId, "customer request", null);

            assertTrue(result.isPresent());
            assertEquals(PaymentStatus.REFUNDED.name(), result.get().newStatus());
            assertEquals(PaymentStatus.REFUNDED, payment.getStatus());
            verify(ledgerRepository).saveAll(eq(tenantId), anyList());
            verify(auditRepository).save(eq(tenantId), any());
        }

        @Test
        @DisplayName("returns empty when payment not found")
        void notFound() {
            when(paymentRepository.findByTenantAndId(tenantId, paymentId)).thenReturn(Optional.empty());
            assertTrue(service.refund(tenantId, paymentId, "x", null).isEmpty());
        }
    }
}