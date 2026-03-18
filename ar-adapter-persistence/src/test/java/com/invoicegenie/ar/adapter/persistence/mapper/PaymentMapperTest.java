package com.invoicegenie.ar.adapter.persistence.mapper;

import com.invoicegenie.ar.adapter.persistence.entity.PaymentAllocationEntity;
import com.invoicegenie.ar.adapter.persistence.entity.PaymentEntity;
import com.invoicegenie.ar.domain.model.customer.CustomerId;
import com.invoicegenie.ar.domain.model.invoice.InvoiceId;
import com.invoicegenie.ar.domain.model.payment.Payment;
import com.invoicegenie.ar.domain.model.payment.PaymentAllocation;
import com.invoicegenie.ar.domain.model.payment.PaymentId;
import com.invoicegenie.ar.domain.model.payment.PaymentMethod;
import com.invoicegenie.shared.domain.Money;
import com.invoicegenie.shared.domain.TenantId;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

class PaymentMapperTest {

    @Test
    void mapsPaymentToEntityAndBack() {
        TenantId tenantId = TenantId.of(UUID.randomUUID());
        PaymentId paymentId = PaymentId.of(UUID.randomUUID());

        Payment payment = new Payment(paymentId, "PAY-001", CustomerId.of(UUID.randomUUID()),
                Money.of("500.00", "USD"), LocalDate.of(2026, 3, 5), PaymentMethod.BANK_TRANSFER);
        payment.allocate(InvoiceId.of(UUID.randomUUID()), Money.of("100.00", "USD"), null, "partial");

        PaymentMapper mapper = new PaymentMapper();
        PaymentEntity entity = mapper.toEntity(tenantId, payment);
        List<PaymentAllocationEntity> allocations = mapper.toAllocationEntities(tenantId, payment);
        Payment restored = mapper.toDomain(entity, allocations);

        assertEquals(payment.getPaymentNumber(), restored.getPaymentNumber());
        assertEquals(payment.getAmount().getAmount(), restored.getAmount().getAmount());
        assertEquals(payment.getAllocations().size(), restored.getAllocations().size());
    }
}
