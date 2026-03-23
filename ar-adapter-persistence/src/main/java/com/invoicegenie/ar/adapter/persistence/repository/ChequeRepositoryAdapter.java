package com.invoicegenie.ar.adapter.persistence.repository;

import com.invoicegenie.ar.domain.model.customer.CustomerId;
import com.invoicegenie.ar.domain.model.payment.Cheque;
import com.invoicegenie.ar.domain.model.payment.ChequeRepository;
import com.invoicegenie.ar.domain.model.payment.ChequeStatus;
import com.invoicegenie.shared.domain.TenantId;

import jakarta.enterprise.context.ApplicationScoped;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.stream.Collectors;

/**
 * In-memory implementation of ChequeRepository for development.
 * In production, use a JPA-backed implementation.
 */
@ApplicationScoped
public class ChequeRepositoryAdapter implements ChequeRepository {

    // In-memory storage: tenantId -> List<Cheque>
    private final ConcurrentMap<UUID, List<Cheque>> storage = new ConcurrentHashMap<>();

    @Override
    public void save(TenantId tenantId, Cheque cheque) {
        List<Cheque> cheques = storage.computeIfAbsent(tenantId.getValue(), k -> new ArrayList<>());
        // Remove existing if present
        cheques.removeIf(c -> c.getId().equals(cheque.getId()));
        cheques.add(cheque);
    }

    @Override
    public Optional<Cheque> findByTenantAndId(TenantId tenantId, UUID id) {
        return storage.getOrDefault(tenantId.getValue(), List.of()).stream()
                .filter(c -> c.getId().equals(id))
                .findFirst();
    }

    @Override
    public Optional<Cheque> findByTenantAndChequeNumber(TenantId tenantId, String chequeNumber) {
        return storage.getOrDefault(tenantId.getValue(), List.of()).stream()
                .filter(c -> c.getChequeNumber().equals(chequeNumber))
                .findFirst();
    }

    @Override
    public List<Cheque> findByTenantAndCustomer(TenantId tenantId, CustomerId customerId) {
        return storage.getOrDefault(tenantId.getValue(), List.of()).stream()
                .filter(c -> c.getCustomerId().equals(customerId))
                .collect(Collectors.toList());
    }

    @Override
    public List<Cheque> findByTenantAndStatus(TenantId tenantId, ChequeStatus status) {
        return storage.getOrDefault(tenantId.getValue(), List.of()).stream()
                .filter(c -> c.getStatus() == status)
                .collect(Collectors.toList());
    }
}
