package com.invoicegenie.ar.application.service;

import com.invoicegenie.ar.application.port.inbound.ChequeUseCase;
import com.invoicegenie.ar.application.port.inbound.InvoiceLifecycleUseCase;
import com.invoicegenie.ar.application.port.inbound.PaymentAllocationUseCase;
import com.invoicegenie.ar.application.port.inbound.RecordPaymentUseCase;
import com.invoicegenie.ar.domain.model.customer.CustomerId;
import com.invoicegenie.ar.domain.model.invoice.Invoice;
import com.invoicegenie.ar.domain.model.invoice.InvoiceId;
import com.invoicegenie.ar.domain.model.invoice.InvoiceLine;
import com.invoicegenie.ar.domain.model.invoice.InvoiceRepository;
import com.invoicegenie.ar.domain.model.ledger.LedgerRepository;
import com.invoicegenie.ar.domain.model.payment.Payment;
import com.invoicegenie.ar.domain.model.payment.PaymentId;
import com.invoicegenie.ar.domain.model.payment.PaymentMethod;
import com.invoicegenie.ar.domain.model.payment.PaymentRepository;
import com.invoicegenie.ar.domain.model.payment.PaymentStatus;
import com.invoicegenie.ar.domain.service.LedgerService;
import com.invoicegenie.ar.domain.model.payment.Cheque;
import com.invoicegenie.ar.domain.model.payment.ChequeRepository;
import com.invoicegenie.ar.domain.model.payment.ChequeStatus;
import com.invoicegenie.ar.domain.service.ChequeService;
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

@DisplayName("ChequeApplicationService")
@ExtendWith(MockitoExtension.class)
class ChequeApplicationServiceTest {

    @Mock private ChequeRepository chequeRepository;
    @Mock private InvoiceLifecycleUseCase invoiceLifecycleUseCase;
    @Mock private LedgerRepository ledgerRepository;
    @Mock private RecordPaymentUseCase recordPaymentUseCase;
    @Mock private PaymentAllocationUseCase paymentAllocationUseCase;
    @Mock private PaymentRepository paymentRepository;
    @Mock private InvoiceRepository invoiceRepository;
    private ChequeService chequeService;
    private ChequeApplicationService service;
    private TenantId tenantId;
    private UUID customerUuid;

    @BeforeEach
    void setUp() {
        chequeService = new ChequeService();
        service = new ChequeApplicationService(chequeService, chequeRepository, invoiceLifecycleUseCase, ledgerRepository);
        tenantId = TenantId.of(UUID.randomUUID());
        customerUuid = UUID.randomUUID();
    }

    private Cheque newCheque() {
        return new Cheque(UUID.randomUUID(), "CHQ-001", new CustomerId(customerUuid),
                Money.of("500.00", "USD"), "Bank of Test", "Main", LocalDate.now(), null);
    }

    @Nested
    @DisplayName("create")
    class Create {
        @Test
        @DisplayName("should create and save cheque")
        void shouldCreate() {
            Cheque cheque = service.create(tenantId, new ChequeUseCase.CreateChequeCommand(
                    "CHQ-001", customerUuid.toString(), Money.of("500.00", "USD"),
                    "Bank of Test", "Main", LocalDate.now(), "notes"));

            assertEquals(ChequeStatus.RECEIVED, cheque.getStatus());
            assertEquals("CHQ-001", cheque.getChequeNumber());
            verify(chequeRepository).save(eq(tenantId), any(Cheque.class));
        }
    }

    @Nested
    @DisplayName("deposit / clear")
    class DepositClear {
        @Test
        @DisplayName("should deposit received cheque")
        void shouldDeposit() {
            Cheque cheque = newCheque();
            when(chequeRepository.findByTenantAndId(eq(tenantId), eq(cheque.getId())))
                    .thenReturn(Optional.of(cheque));

            var result = service.deposit(tenantId, cheque.getId());

            assertTrue(result.isPresent());
            assertTrue(result.get().success());
            assertEquals(ChequeStatus.DEPOSITED, result.get().cheque().getStatus());
            verify(chequeRepository).save(eq(tenantId), eq(cheque));
        }

        @Test
        @DisplayName("should return empty when cheque not found")
        void shouldReturnEmptyWhenNotFound() {
            when(chequeRepository.findByTenantAndId(any(), any())).thenReturn(Optional.empty());

            assertTrue(service.deposit(tenantId, UUID.randomUUID()).isEmpty());
        }

        @Test
        @DisplayName("should clear deposited cheque (status; payment path when wired)")
        void shouldClear() {
            Cheque cheque = newCheque();
            cheque.deposit();
            when(chequeRepository.findByTenantAndId(eq(tenantId), eq(cheque.getId())))
                    .thenReturn(Optional.of(cheque));

            var result = service.clear(tenantId, cheque.getId());

            assertTrue(result.isPresent());
            assertTrue(result.get().success());
            assertEquals(ChequeStatus.CLEARED, result.get().cheque().getStatus());
            // Without RecordPaymentUseCase wired, ledger posts via payment layer are skipped
            assertTrue(result.get().ledgerEntries().isEmpty());
            verify(chequeRepository).save(eq(tenantId), eq(cheque));
        }
    }

    @Nested
    @DisplayName("bounce")
    class Bounce {
        @Test
        @DisplayName("should bounce deposited cheque and save")
        void shouldBounceAndReopen() {
            Cheque cheque = newCheque();
            cheque.deposit();
            when(chequeRepository.findByTenantAndId(eq(tenantId), eq(cheque.getId())))
                    .thenReturn(Optional.of(cheque));

            var result = service.bounce(tenantId, cheque.getId(), "NSF");

            assertTrue(result.isPresent());
            assertTrue(result.get().success());
            assertEquals(ChequeStatus.BOUNCED, result.get().cheque().getStatus());
            verify(chequeRepository).save(eq(tenantId), eq(cheque));
            // No allocated invoices on a plain received→deposited cheque
            assertTrue(result.get().affectedInvoiceIds().isEmpty());
            verifyNoInteractions(invoiceLifecycleUseCase);
        }
    }

    @Nested
    @DisplayName("list")
    class ListCheques {
        @Test
        @DisplayName("should list by status")
        void shouldListByStatus() {
            when(chequeRepository.findByTenantAndStatus(tenantId, ChequeStatus.RECEIVED))
                    .thenReturn(List.of(newCheque()));

            var result = service.list(tenantId, "RECEIVED");

            assertTrue(result.success());
            assertEquals(1, result.cheques().size());
        }

        @Test
        @DisplayName("should reject invalid status")
        void shouldRejectInvalidStatus() {
            var result = service.list(tenantId, "NOPE");
            assertFalse(result.success());
        }

        @Test
        @DisplayName("should list all cheques when status is null")
        void shouldListAllWhenStatusNull() {
            when(chequeRepository.findByTenant(tenantId)).thenReturn(List.of(newCheque()));

            var result = service.list(tenantId, null);

            assertTrue(result.success());
            assertEquals(1, result.cheques().size());
            verify(chequeRepository).findByTenant(tenantId);
        }
    }
    @Nested
    @DisplayName("clear with payment wiring")
    class ClearWithPayment {
        @Test
        @DisplayName("creates payment and allocates to target invoices")
        void clearAllocates() {
            Cheque cheque = newCheque();
            cheque.deposit();
            InvoiceId invId = InvoiceId.generate();
            cheque.addAllocatedInvoice(invId.getValue());
            Invoice invoice = new Invoice(invId, "INV-C1", new CustomerId(customerUuid), "C",
                    "USD", LocalDate.now(), LocalDate.now().plusDays(30), List.of());
            invoice.addLine(new InvoiceLine(1, "S", Money.of("500.00", "USD")));
            invoice.issue();

            PaymentId pid = PaymentId.generate();
            service = new ChequeApplicationService(chequeService, chequeRepository, invoiceLifecycleUseCase,
                    ledgerRepository, recordPaymentUseCase, paymentAllocationUseCase,
                    paymentRepository, invoiceRepository, new LedgerService());

            when(chequeRepository.findByTenantAndId(eq(tenantId), eq(cheque.getId())))
                    .thenReturn(Optional.of(cheque));
            when(recordPaymentUseCase.record(eq(tenantId), any(), anyString())).thenReturn(pid);
            when(invoiceRepository.findByTenantAndId(eq(tenantId), eq(invId))).thenReturn(Optional.of(invoice));
            when(paymentAllocationUseCase.manualAllocate(eq(tenantId), eq(pid), anyList(), any(), anyString()))
                    .thenReturn(Optional.of(new PaymentAllocationUseCase.AllocationResult(
                            pid, List.of(), Money.of("500.00", "USD"), Money.of("0.00", "USD"), List.of(), 1L)));

            var result = service.clear(tenantId, cheque.getId());

            assertTrue(result.isPresent());
            assertTrue(result.get().success());
            assertEquals(ChequeStatus.CLEARED, result.get().cheque().getStatus());
            assertEquals(pid.getValue(), cheque.getPaymentId());
            verify(recordPaymentUseCase).record(eq(tenantId), any(), contains("cheque-clear:"));
            verify(paymentAllocationUseCase).manualAllocate(eq(tenantId), eq(pid), anyList(), any(), anyString());
            verify(chequeRepository).save(eq(tenantId), eq(cheque));
        }

        @Test
        @DisplayName("idempotent clear when already cleared with payment")
        void clearIdempotent() {
            Cheque cheque = newCheque();
            cheque.deposit();
            cheque.clear();
            UUID existingPay = UUID.randomUUID();
            cheque.linkPayment(existingPay);
            when(chequeRepository.findByTenantAndId(eq(tenantId), eq(cheque.getId())))
                    .thenReturn(Optional.of(cheque));

            var result = service.clear(tenantId, cheque.getId());

            assertTrue(result.isPresent());
            assertTrue(result.get().success());
            assertTrue(result.get().message().contains("already cleared"));
            verify(recordPaymentUseCase, never()).record(any(), any(), any());
        }

        @Test
        @DisplayName("FIFO allocate when no invoice targets")
        void clearFifo() {
            Cheque cheque = newCheque();
            cheque.deposit();
            PaymentId pid = PaymentId.generate();
            service = new ChequeApplicationService(chequeService, chequeRepository, invoiceLifecycleUseCase,
                    ledgerRepository, recordPaymentUseCase, paymentAllocationUseCase,
                    paymentRepository, invoiceRepository, new LedgerService());

            when(chequeRepository.findByTenantAndId(eq(tenantId), eq(cheque.getId())))
                    .thenReturn(Optional.of(cheque));
            when(recordPaymentUseCase.record(eq(tenantId), any(), anyString())).thenReturn(pid);
            when(paymentAllocationUseCase.autoAllocateFIFO(eq(tenantId), eq(pid), any(), anyString()))
                    .thenReturn(Optional.of(new PaymentAllocationUseCase.AllocationResult(
                            pid,
                            List.of(new PaymentAllocationUseCase.AllocationResult.AllocationDetail(
                                    InvoiceId.generate(), Money.of("500.00", "USD"), UUID.randomUUID())),
                            Money.of("500.00", "USD"), Money.of("0.00", "USD"), List.of(), 1L)));

            var result = service.clear(tenantId, cheque.getId(), null);

            assertTrue(result.isPresent());
            assertTrue(result.get().success());
            verify(paymentAllocationUseCase).autoAllocateFIFO(eq(tenantId), eq(pid), any(), anyString());
        }
    }

    @Nested
    @DisplayName("bounce cleared with payment reverse")
    class BounceCleared {
        @Test
        @DisplayName("reverses payment allocations and ledger")
        void bounceReverses() {
            Cheque cheque = newCheque();
            cheque.deposit();
            cheque.clear();
            PaymentId pid = PaymentId.generate();
            cheque.linkPayment(pid.getValue());

            InvoiceId invId = InvoiceId.generate();
            Invoice invoice = new Invoice(invId, "INV-B1", new CustomerId(customerUuid), "C",
                    "USD", LocalDate.now(), LocalDate.now().plusDays(30), List.of());
            invoice.addLine(new InvoiceLine(1, "S", Money.of("500.00", "USD")));
            invoice.issue();
            invoice.recordPaymentApplied(Money.of("500.00", "USD"));

            Payment payment = new Payment(pid, "PAY-CHQ", new CustomerId(customerUuid),
                    Money.of("500.00", "USD"), LocalDate.now(), PaymentMethod.CHECK);
            payment.allocate(invId, Money.of("500.00", "USD"), UUID.randomUUID(), null);

            service = new ChequeApplicationService(chequeService, chequeRepository, invoiceLifecycleUseCase,
                    ledgerRepository, recordPaymentUseCase, paymentAllocationUseCase,
                    paymentRepository, invoiceRepository, new LedgerService());

            when(chequeRepository.findByTenantAndId(eq(tenantId), eq(cheque.getId())))
                    .thenReturn(Optional.of(cheque));
            when(paymentRepository.findByTenantAndId(eq(tenantId), eq(pid))).thenReturn(Optional.of(payment));
            when(invoiceRepository.findByTenantAndId(eq(tenantId), eq(invId))).thenReturn(Optional.of(invoice));

            var result = service.bounce(tenantId, cheque.getId(), "NSF");

            assertTrue(result.isPresent());
            assertTrue(result.get().success());
            assertEquals(ChequeStatus.BOUNCED, result.get().cheque().getStatus());
            assertEquals(PaymentStatus.REVERSED, payment.getStatus());
            assertEquals(1, result.get().affectedInvoiceIds().size());
            verify(paymentRepository).save(eq(tenantId), eq(payment));
            verify(invoiceRepository).save(eq(tenantId), eq(invoice));
            verify(ledgerRepository).saveAll(eq(tenantId), anyList());
            verify(chequeRepository).save(eq(tenantId), eq(cheque));
        }
    }

    @Nested
    @DisplayName("get / bulkCreate / ocr confidence")
    class ExtraPaths {
        @Test
        @DisplayName("get returns cheque")
        void get() {
            Cheque cheque = newCheque();
            when(chequeRepository.findByTenantAndId(tenantId, cheque.getId())).thenReturn(Optional.of(cheque));
            assertTrue(service.get(tenantId, cheque.getId()).isPresent());
        }

        @Test
        @DisplayName("bulkCreate empty list")
        void bulkEmpty() {
            assertTrue(service.bulkCreate(tenantId, null).isEmpty());
            assertTrue(service.bulkCreate(tenantId, List.of()).isEmpty());
        }

        @Test
        @DisplayName("bulkCreate creates multiple")
        void bulkCreate() {
            var cmds = List.of(
                    new ChequeUseCase.CreateChequeCommand(
                            "CHQ-A", customerUuid.toString(), Money.of("10.00", "USD"),
                            "Bank", "Br", LocalDate.now(), null),
                    new ChequeUseCase.CreateChequeCommand(
                            "CHQ-B", customerUuid.toString(), Money.of("20.00", "USD"),
                            "Bank", "Br", LocalDate.now(), null, List.of(UUID.randomUUID()), 0.9));
            // CreateChequeCommand may not have all overloads — handle below if compile fails
            var created = service.bulkCreate(tenantId, cmds);
            assertEquals(2, created.size());
            verify(chequeRepository, times(2)).save(eq(tenantId), any(Cheque.class));
        }

        @Test
        @DisplayName("rejects low OCR confidence")
        void lowOcr() {
            service = new ChequeApplicationService(chequeService, chequeRepository, invoiceLifecycleUseCase,
                    ledgerRepository, null, null, null, null, null, null, 0.8);
            assertThrows(IllegalArgumentException.class, () ->
                    service.create(tenantId, new ChequeUseCase.CreateChequeCommand(
                            "CHQ-L", customerUuid.toString(), Money.of("10.00", "USD"),
                            "Bank", "Br", LocalDate.now(), null, null, 0.2)));
        }
    }
}
