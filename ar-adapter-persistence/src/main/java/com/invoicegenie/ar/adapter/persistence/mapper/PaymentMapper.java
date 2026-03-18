package com.invoicegenie.ar.adapter.persistence.mapper;

import com.invoicegenie.ar.adapter.persistence.entity.PaymentAllocationEntity;
import com.invoicegenie.ar.adapter.persistence.entity.PaymentEntity;
import com.invoicegenie.ar.domain.model.customer.CustomerId;
import com.invoicegenie.ar.domain.model.invoice.InvoiceId;
import com.invoicegenie.ar.domain.model.payment.Payment;
import com.invoicegenie.ar.domain.model.payment.PaymentAllocation;
import com.invoicegenie.ar.domain.model.payment.PaymentId;
import com.invoicegenie.shared.domain.Money;
import com.invoicegenie.shared.domain.TenantId;

import java.util.List;
import java.util.UUID;

/**
 * Maps Payment aggregate ↔ JPA entities.
 */
public final class PaymentMapper {

    public PaymentEntity toEntity(TenantId tenantId, Payment payment) {
        PaymentEntity e = new PaymentEntity();
        e.setId(payment.getId().getValue());
        e.setTenantId(tenantId.getValue());
        e.setPaymentNumber(payment.getPaymentNumber());
        e.setCustomerId(payment.getCustomerId().getValue());
        e.setCurrency(payment.getAmount().getCurrencyCode());
        e.setAmount(payment.getAmount().getAmount());
        e.setAmountUnallocated(payment.getAmountUnallocated().getAmount());
        e.setPaymentDate(payment.getPaymentDate());
        e.setReceivedAt(payment.getReceivedAt());
        e.setMethod(payment.getMethod());
        e.setReference(payment.getReference());
        e.setBankAccountId(payment.getBankAccountId());
        e.setNotes(payment.getNotes());
        e.setStatus(payment.getStatus());
        e.setCreatedAt(payment.getCreatedAt());
        e.setUpdatedAt(payment.getUpdatedAt());
        e.setVersion(payment.getVersion());
        return e;
    }

    public List<PaymentAllocationEntity> toAllocationEntities(TenantId tenantId, Payment payment) {
        return payment.getAllocations().stream()
                .map(alloc -> toAllocationEntity(tenantId, payment.getId(), alloc))
                .toList();
    }

    public PaymentAllocationEntity toAllocationEntity(TenantId tenantId, PaymentId paymentId, PaymentAllocation alloc) {
        PaymentAllocationEntity e = new PaymentAllocationEntity();
        e.setId(alloc.getId());
        e.setTenantId(tenantId.getValue());
        e.setPaymentId(paymentId.getValue());
        e.setInvoiceId(alloc.getInvoiceId().getValue());
        e.setAmount(alloc.getAmount().getAmount());
        e.setCurrency(alloc.getAmount().getCurrencyCode());
        e.setAllocatedAt(alloc.getAllocatedAt());
        e.setAllocatedBy(alloc.getAllocatedBy());
        e.setNotes(alloc.getNotes());
        return e;
    }

    public Payment toDomain(PaymentEntity e, List<PaymentAllocationEntity> allocationEntities) {
        List<PaymentAllocation> allocations = allocationEntities.stream()
                .map(ae -> new PaymentAllocation(
                        ae.getId(),
                        InvoiceId.of(ae.getInvoiceId()),
                        Money.of(ae.getAmount(), ae.getCurrency()),
                        ae.getAllocatedAt(),
                        ae.getAllocatedBy(),
                        ae.getNotes()
                ))
                .toList();

        return new Payment(
                PaymentId.of(e.getId()),
                e.getPaymentNumber(),
                CustomerId.of(e.getCustomerId()),
                Money.of(e.getAmount(), e.getCurrency()),
                e.getPaymentDate(),
                e.getReceivedAt(),
                e.getMethod(),
                e.getReference(),
                e.getBankAccountId(),
                e.getNotes(),
                e.getStatus(),
                e.getCreatedAt(),
                e.getUpdatedAt(),
                e.getVersion(),
                allocations
        );
    }
}
