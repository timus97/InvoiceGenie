package com.invoicegenie.ar.domain.model.payment;

import com.invoicegenie.ar.domain.model.invoice.InvoiceId;
import com.invoicegenie.shared.domain.TenantId;

import java.util.List;
import java.util.Optional;

/**
 * Port (outbound): persistence for Payment aggregate.
 * All methods require TenantId — enforced by application layer.
 */
public interface PaymentRepository {

    void save(TenantId tenantId, Payment payment);

    Optional<Payment> findByTenantAndId(TenantId tenantId, PaymentId id);

    Optional<Payment> findByTenantAndNumber(TenantId tenantId, String paymentNumber);

    /**
     * Finds payments for a customer with unallocated amount > 0.
     */
    List<Payment> findUnallocatedByTenantAndCustomer(TenantId tenantId, com.invoicegenie.ar.domain.model.customer.CustomerId customerId);

    /**
     * Finds all allocations for a given invoice.
     */
    List<PaymentAllocation> findAllocationsByTenantAndInvoice(TenantId tenantId, InvoiceId invoiceId);
}
