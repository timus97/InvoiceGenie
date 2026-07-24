package com.invoicegenie.ar.adapter.api.rest;

import com.invoicegenie.ar.application.port.inbound.PaymentAllocationUseCase;
import com.invoicegenie.ar.application.port.inbound.PaymentQueryUseCase;
import com.invoicegenie.ar.application.port.inbound.PaymentReversalUseCase;
import com.invoicegenie.ar.application.port.inbound.PaymentUnallocateUseCase;
import com.invoicegenie.ar.application.port.inbound.RecordPaymentUseCase;
import com.invoicegenie.ar.domain.model.payment.PaymentId;
import com.invoicegenie.ar.domain.model.payment.PaymentMethod;
import com.invoicegenie.shared.domain.TenantId;
import com.invoicegenie.shared.tenant.TenantContext;
import jakarta.ws.rs.core.Response;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
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

@DisplayName("PaymentResource")
@ExtendWith(MockitoExtension.class)
class PaymentResourceTest {

    @Mock private PaymentAllocationUseCase allocationUseCase;
    @Mock private RecordPaymentUseCase recordPaymentUseCase;
    @Mock private PaymentQueryUseCase paymentQueryUseCase;
    @Mock private PaymentReversalUseCase paymentReversalUseCase;
    @Mock private PaymentUnallocateUseCase paymentUnallocateUseCase;

    private PaymentResource resource;
    private TenantId tenantId;

    @BeforeEach
    void setUp() {
        resource = new PaymentResource(allocationUseCase, recordPaymentUseCase,
                paymentQueryUseCase, paymentReversalUseCase, paymentUnallocateUseCase);
        tenantId = TenantId.of(UUID.randomUUID());
        TenantContext.setCurrentTenant(tenantId);
    }

    @AfterEach
    void tearDown() {
        TenantContext.clear();
    }

    @Test
    @DisplayName("create returns 201")
    void createOk() {
        PaymentId id = PaymentId.of(UUID.randomUUID());
        when(recordPaymentUseCase.record(eq(tenantId), any(), isNull())).thenReturn(id);

        var dto = new PaymentResource.CreatePaymentRequestDto(
                "PAY-1", UUID.randomUUID().toString(), new BigDecimal("50.00"), "USD",
                LocalDate.now(), PaymentMethod.BANK_TRANSFER, "ref", null);

        Response r = resource.create(null, dto);
        assertEquals(201, r.getStatus());
    }

    @Test
    @DisplayName("create with idempotency key returns 201")
    void createWithIdempotency() {
        PaymentId id = PaymentId.of(UUID.randomUUID());
        when(recordPaymentUseCase.record(eq(tenantId), any(), eq("pay-key"))).thenReturn(id);

        var dto = new PaymentResource.CreatePaymentRequestDto(
                "PAY-1", UUID.randomUUID().toString(), new BigDecimal("50.00"), "USD",
                LocalDate.now(), PaymentMethod.BANK_TRANSFER, "ref", null);

        assertEquals(201, resource.create("pay-key", dto).getStatus());
    }

    @Test
    @DisplayName("create propagates validation error for global mapper")
    void createBad() {
        when(recordPaymentUseCase.record(eq(tenantId), any(), any()))
                .thenThrow(new IllegalArgumentException("bad"));

        var dto = new PaymentResource.CreatePaymentRequestDto(
                "PAY-1", UUID.randomUUID().toString(), new BigDecimal("50.00"), "USD",
                LocalDate.now(), PaymentMethod.BANK_TRANSFER, null, null);

        assertThrows(IllegalArgumentException.class, () -> resource.create(null, dto));
    }

    @Test
    @DisplayName("fifo allocate returns 404 when payment missing")
    void fifoNotFound() {
        when(allocationUseCase.autoAllocateFIFO(any(), any(), any(), any()))
                .thenReturn(Optional.empty());

        var dto = new PaymentResource.AllocationRequestDto(UUID.randomUUID().toString());
        assertEquals(404, resource.autoAllocateFIFO(UUID.randomUUID().toString(), null, dto).getStatus());
    }

    @Test
    @DisplayName("unallocate returns 200")
    void unallocateOk() {
        PaymentId id = PaymentId.of(UUID.randomUUID());
        UUID invId = UUID.randomUUID();
        when(paymentUnallocateUseCase.unallocate(eq(tenantId), eq(id), any(), eq("fix"), isNull()))
                .thenReturn(Optional.of(new PaymentUnallocateUseCase.UnallocateResult(
                        id, "RECEIVED", List.of(invId), 3L, "ok")));

        var dto = new PaymentResource.UnallocateRequestDto(
                List.of(invId.toString()), "fix", null);
        Response r = resource.unallocate(id.getValue().toString(), dto);
        assertEquals(200, r.getStatus());
    }

    @Test
    @DisplayName("unallocate returns 400 when invoiceIds missing")
    void unallocateBadRequest() {
        Response r = resource.unallocate(UUID.randomUUID().toString(),
                new PaymentResource.UnallocateRequestDto(List.of(), "x", null));
        assertEquals(400, r.getStatus());
    }
}