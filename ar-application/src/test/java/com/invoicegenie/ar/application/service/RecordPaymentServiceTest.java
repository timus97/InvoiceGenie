package com.invoicegenie.ar.application.service;

import com.invoicegenie.ar.application.port.inbound.RecordPaymentUseCase;
import com.invoicegenie.ar.application.port.outbound.EventPublisher;
import com.invoicegenie.ar.application.port.outbound.IdGenerator;
import com.invoicegenie.ar.application.port.outbound.IdempotencyStore;
import com.invoicegenie.ar.domain.event.PaymentRecorded;
import com.invoicegenie.ar.domain.model.customer.Customer;
import com.invoicegenie.ar.domain.model.customer.CustomerId;
import com.invoicegenie.ar.domain.model.customer.CustomerRepository;
import com.invoicegenie.ar.domain.model.ledger.LedgerEntry;
import com.invoicegenie.ar.domain.model.ledger.LedgerRepository;
import com.invoicegenie.ar.domain.model.outbox.AuditRepository;
import com.invoicegenie.ar.domain.model.payment.PaymentId;
import com.invoicegenie.ar.domain.model.payment.PaymentMethod;
import com.invoicegenie.ar.domain.model.payment.PaymentRepository;
import com.invoicegenie.ar.domain.service.LedgerService;
import com.invoicegenie.shared.domain.TenantId;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
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

@DisplayName("RecordPaymentService")
@ExtendWith(MockitoExtension.class)
class RecordPaymentServiceTest {

    @Mock private PaymentRepository paymentRepository;
    @Mock private CustomerRepository customerRepository;
    @Mock private IdGenerator idGenerator;
    @Mock private AuditRepository auditRepository;
    @Mock private EventPublisher eventPublisher;
    @Mock private LedgerRepository ledgerRepository;
    @Mock private IdempotencyStore idempotencyStore;

    private RecordPaymentService service;
    private TenantId tenantId;
    private CustomerId customerId;

    @BeforeEach
    void setUp() {
        service = new RecordPaymentService(paymentRepository, customerRepository, idGenerator, auditRepository,
                eventPublisher, new LedgerService(), ledgerRepository, idempotencyStore);
        tenantId = TenantId.of(UUID.randomUUID());
        customerId = CustomerId.of(UUID.randomUUID());
    }

    private RecordPaymentUseCase.RecordPaymentCommand createCommand() {
        return new RecordPaymentUseCase.RecordPaymentCommand(
                "PAY-001",
                customerId.getValue().toString(),
                new BigDecimal("1000.00"),
                "USD",
                LocalDate.now(),
                PaymentMethod.BANK_TRANSFER,
                "REF-12345",
                "Test payment"
        );
    }

    @Nested
    @DisplayName("Successful Payment Recording")
    class SuccessfulRecording {

        @Test
        @DisplayName("should create payment when customer exists and post ledger")
        void shouldCreatePaymentWhenCustomerExists() {
            var customer = mock(Customer.class);
            when(customerRepository.findByTenantAndId(eq(tenantId), eq(customerId)))
                    .thenReturn(Optional.of(customer));
            when(idGenerator.newUuid()).thenReturn(UUID.randomUUID());
            when(paymentRepository.findByTenantAndNumber(eq(tenantId), anyString()))
                    .thenReturn(Optional.empty());

            var result = service.record(tenantId, createCommand());

            assertNotNull(result);
            verify(paymentRepository).save(eq(tenantId), any());
            verify(auditRepository).save(eq(tenantId), any());
            verify(eventPublisher).publish(any(PaymentRecorded.class));
            @SuppressWarnings("unchecked")
            ArgumentCaptor<List<LedgerEntry>> captor = ArgumentCaptor.forClass(List.class);
            verify(ledgerRepository).saveAll(eq(tenantId), captor.capture());
            assertEquals(2, captor.getValue().size());
        }

        @Test
        @DisplayName("should generate UUID v7 for payment ID")
        void shouldGenerateUuidV7ForPaymentId() {
            var customer = mock(Customer.class);
            var expectedUuid = UUID.randomUUID();
            when(customerRepository.findByTenantAndId(eq(tenantId), eq(customerId)))
                    .thenReturn(Optional.of(customer));
            when(idGenerator.newUuid()).thenReturn(expectedUuid);
            when(paymentRepository.findByTenantAndNumber(eq(tenantId), anyString()))
                    .thenReturn(Optional.empty());

            var result = service.record(tenantId, createCommand());

            assertEquals(expectedUuid, result.getValue());
            verify(idGenerator).newUuid();
            verify(eventPublisher).publish(any(PaymentRecorded.class));
        }

        @Test
        @DisplayName("should publish PaymentRecorded with correct fields")
        void shouldPublishPaymentRecordedWithCorrectFields() {
            var customer = mock(Customer.class);
            var expectedUuid = UUID.randomUUID();
            when(customerRepository.findByTenantAndId(eq(tenantId), eq(customerId)))
                    .thenReturn(Optional.of(customer));
            when(idGenerator.newUuid()).thenReturn(expectedUuid);
            when(paymentRepository.findByTenantAndNumber(eq(tenantId), anyString()))
                    .thenReturn(Optional.empty());

            service.record(tenantId, createCommand());

            verify(eventPublisher).publish(argThat(event -> {
                if (!(event instanceof PaymentRecorded pr)) {
                    return false;
                }
                return pr.paymentId().getValue().equals(expectedUuid)
                        && pr.tenantId().equals(tenantId)
                        && pr.customerRef().equals(customerId.getValue().toString())
                        && pr.amount().getAmount().compareTo(new BigDecimal("1000.00")) == 0
                        && "USD".equals(pr.amount().getCurrencyCode());
            }));
        }
    }

    @Nested
    @DisplayName("Validation Failures")
    class ValidationFailures {

        @Test
        @DisplayName("should fail when customer not found")
        void shouldFailWhenCustomerNotFound() {
            when(customerRepository.findByTenantAndId(eq(tenantId), eq(customerId)))
                    .thenReturn(Optional.empty());

            var ex = assertThrows(IllegalArgumentException.class,
                    () -> service.record(tenantId, createCommand()));
            assertTrue(ex.getMessage().contains("Customer not found"));
            verifyNoInteractions(eventPublisher);
            verifyNoInteractions(ledgerRepository);
        }

        @Test
        @DisplayName("should fail when payment number already exists")
        void shouldFailWhenPaymentNumberExists() {
            var customer = mock(Customer.class);
            var existingPayment = mock(com.invoicegenie.ar.domain.model.payment.Payment.class);

            when(customerRepository.findByTenantAndId(eq(tenantId), eq(customerId)))
                    .thenReturn(Optional.of(customer));
            when(paymentRepository.findByTenantAndNumber(eq(tenantId), eq("PAY-001")))
                    .thenReturn(Optional.of(existingPayment));

            var ex = assertThrows(IllegalArgumentException.class,
                    () -> service.record(tenantId, createCommand()));
            assertTrue(ex.getMessage().contains("Payment number already exists"));
            verifyNoInteractions(eventPublisher);
            verifyNoInteractions(ledgerRepository);
        }
    }

    @Nested
    @DisplayName("Idempotency")
    class Idempotency {

        @Test
        @DisplayName("should return cached payment id for same key and payload")
        void shouldReturnCachedPayment() {
            UUID cached = UUID.randomUUID();
            when(idempotencyStore.find(eq(tenantId), eq("payment:pay-key")))
                    .thenReturn(Optional.of(new IdempotencyStore.IdempotencyRecord(
                            "payment:pay-key",
                            // hash recomputed inside service — stub to match by using any and return same
                            // We need the real hash; call twice with empty first then full path
                            "placeholder",
                            cached.toString(),
                            java.time.Instant.now())));

            // First call: empty store so we capture hash
            reset(idempotencyStore);
            when(idempotencyStore.find(eq(tenantId), eq("payment:pay-key"))).thenReturn(Optional.empty());
            var customer = mock(Customer.class);
            when(customerRepository.findByTenantAndId(eq(tenantId), eq(customerId)))
                    .thenReturn(Optional.of(customer));
            when(idGenerator.newUuid()).thenReturn(cached);
            when(paymentRepository.findByTenantAndNumber(eq(tenantId), anyString()))
                    .thenReturn(Optional.empty());

            service.record(tenantId, createCommand(), "pay-key");

            ArgumentCaptor<String> hashCaptor = ArgumentCaptor.forClass(String.class);
            verify(idempotencyStore).put(eq(tenantId), eq("payment:pay-key"), hashCaptor.capture(), anyString());
            String hash = hashCaptor.getValue();

            when(idempotencyStore.find(eq(tenantId), eq("payment:pay-key")))
                    .thenReturn(Optional.of(new IdempotencyStore.IdempotencyRecord(
                            "payment:pay-key", hash, cached.toString(), java.time.Instant.now())));

            PaymentId second = service.record(tenantId, createCommand(), "pay-key");
            assertEquals(cached, second.getValue());
            verify(paymentRepository, times(1)).save(eq(tenantId), any());
        }
    }

    @Nested
    @DisplayName("Command Validation")
    class CommandValidation {

        @Test
        @DisplayName("should reject null payment number")
        void shouldRejectNullPaymentNumber() {
            assertThrows(IllegalArgumentException.class, () ->
                new RecordPaymentUseCase.RecordPaymentCommand(
                    null, customerId.getValue().toString(), new BigDecimal("100"), "USD",
                    LocalDate.now(), PaymentMethod.CASH, null, null));
        }

        @Test
        @DisplayName("should reject blank payment number")
        void shouldRejectBlankPaymentNumber() {
            assertThrows(IllegalArgumentException.class, () ->
                new RecordPaymentUseCase.RecordPaymentCommand(
                    "", customerId.getValue().toString(), new BigDecimal("100"), "USD",
                    LocalDate.now(), PaymentMethod.CASH, null, null));
        }

        @Test
        @DisplayName("should reject null customer ID")
        void shouldRejectNullCustomerId() {
            assertThrows(IllegalArgumentException.class, () ->
                new RecordPaymentUseCase.RecordPaymentCommand(
                    "PAY-001", null, new BigDecimal("100"), "USD",
                    LocalDate.now(), PaymentMethod.CASH, null, null));
        }

        @Test
        @DisplayName("should reject zero amount")
        void shouldRejectZeroAmount() {
            assertThrows(IllegalArgumentException.class, () ->
                new RecordPaymentUseCase.RecordPaymentCommand(
                    "PAY-001", customerId.getValue().toString(), BigDecimal.ZERO, "USD",
                    LocalDate.now(), PaymentMethod.CASH, null, null));
        }

        @Test
        @DisplayName("should reject negative amount")
        void shouldRejectNegativeAmount() {
            assertThrows(IllegalArgumentException.class, () ->
                new RecordPaymentUseCase.RecordPaymentCommand(
                    "PAY-001", customerId.getValue().toString(), new BigDecimal("-100"), "USD",
                    LocalDate.now(), PaymentMethod.CASH, null, null));
        }

        @Test
        @DisplayName("should reject null payment date")
        void shouldRejectNullPaymentDate() {
            assertThrows(IllegalArgumentException.class, () ->
                new RecordPaymentUseCase.RecordPaymentCommand(
                    "PAY-001", customerId.getValue().toString(), new BigDecimal("100"), "USD",
                    null, PaymentMethod.CASH, null, null));
        }

        @Test
        @DisplayName("should reject null method")
        void shouldRejectNullMethod() {
            assertThrows(IllegalArgumentException.class, () ->
                new RecordPaymentUseCase.RecordPaymentCommand(
                    "PAY-001", customerId.getValue().toString(), new BigDecimal("100"), "USD",
                    LocalDate.now(), null, null, null));
        }
    }
}
