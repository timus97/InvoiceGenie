package com.invoicegenie.ar.adapter.persistence.repository;

import com.invoicegenie.ar.domain.model.customer.CustomerId;
import com.invoicegenie.ar.domain.model.payment.CreditNote;
import com.invoicegenie.ar.domain.model.payment.CreditNoteRepository;
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
 * In-memory implementation of CreditNoteRepository for development.
 */
@ApplicationScoped
public class CreditNoteRepositoryAdapter implements CreditNoteRepository {

    private final ConcurrentMap<UUID, List<CreditNote>> storage = new ConcurrentHashMap<>();

    @Override
    public void save(TenantId tenantId, CreditNote creditNote) {
        List<CreditNote> creditNotes = storage.computeIfAbsent(tenantId.getValue(), k -> new ArrayList<>());
        creditNotes.removeIf(c -> c.getId().equals(creditNote.getId()));
        creditNotes.add(creditNote);
    }

    @Override
    public Optional<CreditNote> findByTenantAndId(TenantId tenantId, UUID id) {
        return storage.getOrDefault(tenantId.getValue(), List.of()).stream()
                .filter(c -> c.getId().equals(id))
                .findFirst();
    }

    @Override
    public List<CreditNote> findByTenantAndCustomer(TenantId tenantId, CustomerId customerId) {
        return storage.getOrDefault(tenantId.getValue(), List.of()).stream()
                .filter(c -> c.getCustomerId().equals(customerId))
                .collect(Collectors.toList());
    }

    @Override
    public List<CreditNote> findByTenantAndStatus(TenantId tenantId, CreditNote.CreditNoteStatus status) {
        return storage.getOrDefault(tenantId.getValue(), List.of()).stream()
                .filter(c -> c.getStatus() == status)
                .collect(Collectors.toList());
    }

    @Override
    public List<CreditNote> findAvailableByTenantAndCustomer(TenantId tenantId, CustomerId customerId) {
        return storage.getOrDefault(tenantId.getValue(), List.of()).stream()
                .filter(c -> c.getCustomerId().equals(customerId) && c.canApply())
                .collect(Collectors.toList());
    }
}
