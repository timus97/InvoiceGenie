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
import java.util.List;
import java.util.Optional;

/**
 * Driven adapter: implements CustomerRepository port using JPA.
 * 
 * <p>Enforces tenant isolation on all queries.
 * <p>Supports soft delete (DELETED status).
 */
@ApplicationScoped
public class CustomerRepositoryAdapter implements CustomerRepository {

    @PersistenceContext
    EntityManager em;

    private final CustomerMapper mapper = new CustomerMapper();

    @Override
    @Transactional
    public void save(TenantId tenantId, Customer customer) {
        customer.validate(); // Domain validation
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

    @Override
    public List<Customer> findAllByTenant(TenantId tenantId, boolean includeDeleted) {
        String jpql = includeDeleted 
            ? "SELECT c FROM CustomerEntity c WHERE c.tenantId = :tenantId"
            : "SELECT c FROM CustomerEntity c WHERE c.tenantId = :tenantId AND c.status <> :deleted";
        
        var query = em.createQuery(jpql, CustomerEntity.class)
                .setParameter("tenantId", tenantId.getValue());
        
        if (!includeDeleted) {
            query.setParameter("deleted", CustomerStatus.DELETED);
        }
        
        return query.getResultStream()
                .map(mapper::toDomain)
                .toList();
    }

    @Override
    public List<Customer> findByTenantAndStatus(TenantId tenantId, CustomerStatus status) {
        return em.createQuery("SELECT c FROM CustomerEntity c WHERE c.tenantId = :tenantId AND c.status = :status",
                        CustomerEntity.class)
                .setParameter("tenantId", tenantId.getValue())
                .setParameter("status", status)
                .getResultStream()
                .map(mapper::toDomain)
                .toList();
    }

    @Override
    public List<Customer> searchByTenant(TenantId tenantId, String query) {
        String searchPattern = "%" + query.toLowerCase() + "%";
        return em.createQuery(
                        "SELECT c FROM CustomerEntity c WHERE c.tenantId = :tenantId " +
                        "AND (LOWER(c.customerCode) LIKE :q OR LOWER(c.legalName) LIKE :q OR LOWER(c.displayName) LIKE :q)",
                        CustomerEntity.class)
                .setParameter("tenantId", tenantId.getValue())
                .setParameter("q", searchPattern)
                .setMaxResults(50)
                .getResultStream()
                .map(mapper::toDomain)
                .toList();
    }

    @Override
    public long countByTenantAndStatus(TenantId tenantId, CustomerStatus status) {
        return em.createQuery(
                        "SELECT COUNT(c) FROM CustomerEntity c WHERE c.tenantId = :tenantId AND c.status = :status",
                        Long.class)
                .setParameter("tenantId", tenantId.getValue())
                .setParameter("status", status)
                .getSingleResult();
    }

    @Override
    public boolean existsByTenantAndCode(TenantId tenantId, String customerCode) {
        Long count = em.createQuery(
                        "SELECT COUNT(c) FROM CustomerEntity c WHERE c.tenantId = :tenantId AND c.customerCode = :code",
                        Long.class)
                .setParameter("tenantId", tenantId.getValue())
                .setParameter("code", customerCode)
                .getSingleResult();
        return count != null && count > 0;
    }

    @Override
    @Transactional
    public void delete(TenantId tenantId, CustomerId id) {
        CustomerEntity entity = em.find(CustomerEntity.class, id.getValue());
        if (entity != null && entity.getTenantId().equals(tenantId.getValue())) {
            // Soft delete - mark as DELETED
            entity.setStatus(CustomerStatus.DELETED);
            entity.setUpdatedAt(java.time.Instant.now());
            em.merge(entity);
        }
    }
}
