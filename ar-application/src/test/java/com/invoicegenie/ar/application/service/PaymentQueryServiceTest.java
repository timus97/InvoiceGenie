package com.invoicegenie.ar.application.service;

import com.invoicegenie.ar.application.port.inbound.PaymentQueryUseCase;
import com.invoicegenie.ar.domain.model.customer.CustomerId;
import com.invoicegenie.ar.domain.model.payment.Payment;
import com.invoicegenie.ar.domain.model.payment.PaymentId;
import com.invoicegenie.ar.domain.model.payment.PaymentMethod;
import com.invoicegenie.ar.domain.model.payment.PaymentRepository;
import com.invoicegenie.ar.domain.model.payment.PaymentStatus;
import com.invoicegenie.shared.domain.Money;
import com.invoicegenie.shared.domain.TenantId;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@DisplayName("PaymentQueryService")
@ExtendWith(MockitoExtension.class)
class PaymentQueryServiceTest {

    @Mock private PaymentRepository paymentRepository;

    private PaymentQueryService service;
    private TenantId tenantId;
    private CustomerId customerId;

    @BeforeEach
    void setUp() {
        service = new PaymentQueryService(paymentRepository);
        tenantId = TenantId.of(UUID.randomUUID());
        customerId = CustomerId.of(UUID.randomUUID());
    }

    private Payment payment(String number, LocalDate date, Money amount) {
        return new Payment(PaymentId.generate(), number, customerId, amount, date, PaymentMethod.CASH);
    }

    @Nested
    @DisplayName("get")
    class Get {
        @Test
        @DisplayName("returns payment when found")
        void found() {
            Payment p = payment("PAY-1", LocalDate.now(), Money.of("10.00", "USD"));
            when(paymentRepository.findByTenantAndId(tenantId, p.getId())).thenReturn(Optional.of(p));

            assertTrue(service.get(tenantId, p.getId()).isPresent());
        }

        @Test
        @DisplayName("returns empty when missing")
        void missing() {
            PaymentId id = PaymentId.generate();
            when(paymentRepository.findByTenantAndId(tenantId, id)).thenReturn(Optional.empty());
            assertTrue(service.get(tenantId, id).isEmpty());
        }
    }

    @Nested
    @DisplayName("list filters")
    class ListFilters {
        @Test
        @DisplayName("lists by tenant when no customer filter")
        void byTenant() {
            Payment p = payment("PAY-T", LocalDate.now(), Money.of("100.00", "USD"));
            when(paymentRepository.findByTenant(eq(tenantId), eq(50))).thenReturn(List.of(p));

            var result = service.list(tenantId, new PaymentQueryUseCase.PaymentListFilter(
                    null, null, null, null, false, 50));

            assertEquals(1, result.count());
            verify(paymentRepository).findByTenant(tenantId, 50);
        }

        @Test
        @DisplayName("lists by customer")
        void byCustomer() {
            Payment p = payment("PAY-C", LocalDate.now(), Money.of("100.00", "USD"));
            when(paymentRepository.findByTenantAndCustomer(eq(tenantId), eq(customerId)))
                    .thenReturn(List.of(p));

            var result = service.list(tenantId, new PaymentQueryUseCase.PaymentListFilter(
                    customerId.getValue(), null, null, null, false, 50));

            assertEquals(1, result.count());
            verify(paymentRepository).findByTenantAndCustomer(tenantId, customerId);
        }

        @Test
        @DisplayName("lists unallocated by customer")
        void unallocatedByCustomer() {
            Payment p = payment("PAY-U", LocalDate.now(), Money.of("100.00", "USD"));
            when(paymentRepository.findUnallocatedByTenantAndCustomer(eq(tenantId), eq(customerId)))
                    .thenReturn(List.of(p));

            var result = service.list(tenantId, new PaymentQueryUseCase.PaymentListFilter(
                    customerId.getValue(), null, null, null, true, 50));

            assertEquals(1, result.count());
            verify(paymentRepository).findUnallocatedByTenantAndCustomer(tenantId, customerId);
        }

        @Test
        @DisplayName("filters by status and date range")
        void statusAndDates() {
            Payment match = payment("PAY-M", LocalDate.of(2025, 6, 15), Money.of("50.00", "USD"));
            Payment wrongDate = payment("PAY-D", LocalDate.of(2024, 1, 1), Money.of("50.00", "USD"));
            Payment reversed = payment("PAY-R", LocalDate.of(2025, 6, 20), Money.of("50.00", "USD"));
            reversed.reverse();

            when(paymentRepository.findByTenant(eq(tenantId), eq(50)))
                    .thenReturn(List.of(match, wrongDate, reversed));

            var result = service.list(tenantId, new PaymentQueryUseCase.PaymentListFilter(
                    null, PaymentStatus.RECEIVED,
                    LocalDate.of(2025, 6, 1), LocalDate.of(2025, 6, 30),
                    false, 50));

            assertEquals(1, result.count());
            assertEquals("PAY-M", result.items().get(0).getPaymentNumber());
        }

        @Test
        @DisplayName("unallocatedOnly filters out fully allocated")
        void unallocatedOnlyFilter() {
            Payment open = payment("PAY-OPEN", LocalDate.now(), Money.of("100.00", "USD"));
            Payment full = payment("PAY-FULL", LocalDate.now(), Money.of("100.00", "USD"));
            full.allocate(com.invoicegenie.ar.domain.model.invoice.InvoiceId.generate(),
                    Money.of("100.00", "USD"), UUID.randomUUID(), null);

            when(paymentRepository.findByTenant(eq(tenantId), eq(50)))
                    .thenReturn(List.of(open, full));

            var result = service.list(tenantId, new PaymentQueryUseCase.PaymentListFilter(
                    null, null, null, null, true, 50));

            assertEquals(1, result.count());
            assertEquals("PAY-OPEN", result.items().get(0).getPaymentNumber());
        }

        @Test
        @DisplayName("limit defaults and caps")
        void limitBounds() {
            when(paymentRepository.findByTenant(eq(tenantId), eq(50))).thenReturn(List.of());
            service.list(tenantId, new PaymentQueryUseCase.PaymentListFilter(
                    null, null, null, null, false, 0));
            verify(paymentRepository).findByTenant(tenantId, 50);

            when(paymentRepository.findByTenant(eq(tenantId), eq(200))).thenReturn(List.of());
            service.list(tenantId, new PaymentQueryUseCase.PaymentListFilter(
                    null, null, null, null, false, 500));
            verify(paymentRepository).findByTenant(tenantId, 200);
        }
    }
}