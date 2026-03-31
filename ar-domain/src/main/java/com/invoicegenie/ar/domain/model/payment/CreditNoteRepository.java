package com.invoicegenie.ar.domain.model.payment;

import com.invoicegenie.shared.domain.TenantId;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Port (outbound): persistence for CreditNote aggregate.
 */
public interface CreditNoteRepository {

    void save(TenantId tenantId, CreditNote creditNote);

    Optional<CreditNote> findByTenantAndId(TenantId tenantId, UUID id);

    List<CreditNote> findByTenantAndCustomer(TenantId tenantId, com.invoicegenie.ar.domain.model.customer.CustomerId customerId);

    List<CreditNote> findByTenantAndStatus(TenantId tenantId, CreditNote.CreditNoteStatus status);

    List<CreditNote> findAvailableByTenantAndCustomer(TenantId tenantId, com.invoicegenie.ar.domain.model.customer.CustomerId customerId);
}
