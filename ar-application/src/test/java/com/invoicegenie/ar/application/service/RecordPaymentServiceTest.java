package com.invoicegenie.ar.application.service;

import com.invoicegenie.ar.application.port.inbound.RecordPaymentUseCase;
import com.invoicegenie.ar.application.port.outbound.IdGenerator;
import com.invoicegenie.ar.domain.model.customer.Customer;
import com.invoicegenie.ar.domain.model.customer.CustomerId;
import com.invoicegenie.ar.domain.model.customer.CustomerRepository;
import com.invoicegenie.ar.domain.model.outbox.AuditRepository;
import com.invoicegenie.ar.domain.model.payment.PaymentId;
import com.invoicegenie.ar.domain.model.payment.PaymentMethod;
import com.invoicegenie.ar.domain.model.payment.PaymentRepository;
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
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@DisplayName("RecordPaymentService")
@ExtendWith(MockitoExtension.class)
class RecordPaymentServiceTest {

    @Mock
    private PaymentRepository paymentRepository;

    @Mock
    private CustomerRepository customerRepository;

    @Mock
    private IdGenerator idGenerator;

    @Mock
    private AuditRepository auditRepository;

    private RecordPaymentService service;
    private TenantId tenantId;
    private CustomerId customerId;

    @BeforeEach
    void setUp() {
        service = new RecordPaymentService(paymentRepository, customerRepository, idGenerator, auditRepository);
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
        @DisplayName("should create payment when customer exists")
        void shouldCreatePaymentWhenCustomerExists() {
            // Arrange
            var customer = mock(Customer.class);
            when(customerRepository.findByTenantAndId(eq(tenantId), eq(customerId)))
                    .thenReturn(Optional.of(customer));
            when(idGenerator.newUuid()).thenReturn(UUID.randomUUID());
            when(paymentRepository.findByTenantAndNumber(eq(tenantId), anyString()))
                    .thenReturn(Optional.empty());

            var command = createCommand();

            // Act
            var result = service.record(tenantId, command);

            // Assert
            assertNotNull(result);
            assertNotNull(result.getValue());
            verify(paymentRepository).save(eq(tenantId), any());
            verify(auditRepository).save(eq(tenantId), any());
        }

        @Test
        @DisplayName("should generate UUID v7 for payment ID")
        void shouldGenerateUuidV7ForPaymentId() {
            // Arrange
            var customer = mock(Customer.class);
            var expectedUuid = UUID.randomUUID();
            when(customerRepository.findByTenantAndId(eq(tenantId), eq(customerId)))
                    .thenReturn(Optional.of(customer));
            when(idGenerator.newUuid()).thenReturn(expectedUuid);
            when(paymentRepository.findByTenantAndNumber(eq(tenantId), anyString()))
                    .thenReturn(Optional.empty());

            var command = createCommand();

            // Act
            var result = service.record(tenantId, command);

            // Assert
            assertEquals(expectedUuid, result.getValue());
            verify(idGenerator).newUuid();
        }
    }

    @Nested
    @DisplayName("Validation Failures")
    class ValidationFailures {

        @Test
        @DisplayName("should fail when customer not found")
        void shouldFailWhenCustomerNotFound() {
            // Arrange
            when(customerRepository.findByTenantAndId(eq(tenantId), eq(customerId)))
                    .thenReturn(Optional.empty());

            var command = createCommand();

            // Act & Assert
            var ex = assertThrows(IllegalArgumentException.class, 
                    () -> service.record(tenantId, command));
            assertTrue(ex.getMessage().contains("Customer not found"));
        }

        @Test
        @DisplayName("should fail when payment number already exists")
        void shouldFailWhenPaymentNumberExists() {
            // Arrange
            var customer = mock(Customer.class);
            var existingPayment = mock(com.invoicegenie.ar.domain.model.payment.Payment.class);
            
            when(customerRepository.findByTenantAndId(eq(tenantId), eq(customerId)))
                    .thenReturn(Optional.of(customer));
            when(paymentRepository.findByTenantAndNumber(eq(tenantId), eq("PAY-001")))
                    .thenReturn(Optional.of(existingPayment));

            var command = createCommand();

            // Act & Assert
            var ex = assertThrows(IllegalArgumentException.class, 
                    () -> service.record(tenantId, command));
            assertTrue(ex.getMessage().contains("Payment number already exists"));
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
