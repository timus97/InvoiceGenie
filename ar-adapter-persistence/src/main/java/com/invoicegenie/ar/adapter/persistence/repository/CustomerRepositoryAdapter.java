package com.invoicegenie.ar.adapter.persistence.repository;

import com.invoicegenie.ar.adapter.persistence.entity.CustomerEntity;
import com.invoicegenie.ar.adapter.persistence.mapper.CustomerMapper;
import com.invoicegenie.ar.domain.model.customer.Customer;
import com.invoicegenie.ar.domain.model.customer.CustomerId;
import com.invoicegenie.ar.domain.model.customer.CustomerRepository;
import com.invoicegenie.ar.domain.model.customer.CustomerStatus;
import com.invoicegenie.shared.domain.TenantId;

import jakarta.enterprise.context.ApplicationScoped;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.transaction.Transactional;
import java.util.Optional;

/**
 * Driven adapter: implements CustomerRepository port.
 */
@ApplicationScoped
public class CustomerRepositoryAdapter implements CustomerRepository {

    @PersistenceContext
    EntityManager em;

    private final CustomerMapper mapper = new CustomerMapper();

    @Override
    @Transactional
    public void save(TenantId tenantId, Customer customer) {
        CustomerEntity entity = mapper.toEntity(tenantId, customer);
        em.merge(entity);
    }

    @Override
    public Optional<Customer> findByTenantAndId(TenantId tenantId, CustomerId id) {
        CustomerEntity e = em.find(CustomerEntity.class, id.getValue());
        if (e == null || !e.getTenantId().equals(tenantId.getValue())) {
            return Optional.empty();
        }
        return Optional.of(mapper.toDomain(e));
    }

    @Override
    public Optional<Customer> findByTenantAndCode(TenantId tenantId, String customerCode) {
        return em.createQuery("SELECT c FROM CustomerEntity c WHERE c.tenantId = :tenantId AND c.customerCode = :code",
                        CustomerEntity.class)
                .setParameter("tenantId", tenantId.getValue())
                .setParameter("code", customerCode)
                .getResultStream()
                .findFirst()
                .map(mapper::toDomain);
    }

    @Override
    public boolean existsActive(TenantId tenantId, CustomerId id) {
        Long count = em.createQuery(
                        "SELECT COUNT(c) FROM CustomerEntity c WHERE c.tenantId = :tenantId AND c.id = :id AND c.status = :status",
                        Long.class)
                .setParameter("tenantId", tenantId.getValue())
                .setParameter("id", id.getValue())
                .setParameter("status", CustomerStatus.ACTIVE)
                .getSingleResult();
        return count != null && count > 0;
    }
}
