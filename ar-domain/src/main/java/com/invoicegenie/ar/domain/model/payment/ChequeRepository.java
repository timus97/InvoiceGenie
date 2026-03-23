package com.invoicegenie.ar.domain.model.payment;

import com.invoicegenie.shared.domain.TenantId;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Port (outbound): persistence for Cheque aggregate.
 */
public interface ChequeRepository {

    void save(TenantId tenantId, Cheque cheque);

    Optional<Cheque> findByTenantAndId(TenantId tenantId, UUID id);

    Optional<Cheque> findByTenantAndChequeNumber(TenantId tenantId, String chequeNumber);

    List<Cheque> findByTenantAndCustomer(TenantId tenantId, com.invoicegenie.ar.domain.model.customer.CustomerId customerId);

    List<Cheque> findByTenantAndStatus(TenantId tenantId, ChequeStatus status);
}
