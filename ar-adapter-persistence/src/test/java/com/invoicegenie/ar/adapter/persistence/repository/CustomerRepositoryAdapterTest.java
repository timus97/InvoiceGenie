package com.invoicegenie.ar.adapter.persistence.repository;

import com.invoicegenie.ar.adapter.persistence.entity.CustomerEntity;
import com.invoicegenie.ar.domain.model.customer.Customer;
import com.invoicegenie.ar.domain.model.customer.CustomerId;
import com.invoicegenie.ar.domain.model.customer.CustomerStatus;
import com.invoicegenie.shared.domain.TenantId;
import jakarta.persistence.EntityManager;
import jakarta.persistence.TypedQuery;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class CustomerRepositoryAdapterTest {

    private CustomerRepositoryAdapter adapter;
    private EntityManager em;

    @BeforeEach
    void setUp() {
        adapter = new CustomerRepositoryAdapter();
        em = mock(EntityManager.class);
        adapter.em = em;
    }

    @Test
    void saveMergesEntity() {
        Customer customer = new Customer(CustomerId.of(UUID.randomUUID()), "CUST-1", "Acme", "USD");
        adapter.save(TenantId.of(UUID.randomUUID()), customer);

        verify(em).merge(any(CustomerEntity.class));
    }

    @Test
    void findByTenantAndId_ReturnsCustomer_WhenFoundAndTenantMatches() {
        UUID id = UUID.randomUUID();
        UUID tenantId = UUID.randomUUID();

        CustomerEntity entity = new CustomerEntity();
        entity.setId(id);
        entity.setTenantId(tenantId);
        entity.setCustomerCode("CUST-1");
        entity.setLegalName("Acme Corp");
        entity.setDisplayName("Acme");
        entity.setCurrency("USD");
        entity.setStatus(CustomerStatus.ACTIVE);
        entity.setCreatedAt(Instant.now());
        entity.setUpdatedAt(Instant.now());

        when(em.find(CustomerEntity.class, id)).thenReturn(entity);

        Optional<Customer> result = adapter.findByTenantAndId(TenantId.of(tenantId), CustomerId.of(id));

        assertTrue(result.isPresent());
        assertEquals("CUST-1", result.get().getCustomerCode());
    }

    @Test
    void findByTenantAndId_ReturnsEmpty_WhenNotFound() {
        UUID id = UUID.randomUUID();
        when(em.find(CustomerEntity.class, id)).thenReturn(null);

        Optional<Customer> result = adapter.findByTenantAndId(TenantId.of(UUID.randomUUID()), CustomerId.of(id));

        assertTrue(result.isEmpty());
    }

    @Test
    void findByTenantAndId_ReturnsEmpty_ForDifferentTenant() {
        UUID id = UUID.randomUUID();
        UUID tenantId = UUID.randomUUID();
        UUID differentTenantId = UUID.randomUUID();

        CustomerEntity entity = new CustomerEntity();
        entity.setId(id);
        entity.setTenantId(differentTenantId);

        when(em.find(CustomerEntity.class, id)).thenReturn(entity);

        Optional<Customer> result = adapter.findByTenantAndId(TenantId.of(tenantId), CustomerId.of(id));

        assertTrue(result.isEmpty());
    }

    @Test
    void findByTenantAndCode_ReturnsCustomer_WhenFound() {
        UUID id = UUID.randomUUID();
        UUID tenantId = UUID.randomUUID();

        CustomerEntity entity = new CustomerEntity();
        entity.setId(id);
        entity.setTenantId(tenantId);
        entity.setCustomerCode("CUST-1");
        entity.setLegalName("Acme");
        entity.setCurrency("USD");
        entity.setStatus(CustomerStatus.ACTIVE);
        entity.setCreatedAt(Instant.now());
        entity.setUpdatedAt(Instant.now());

        TypedQuery<CustomerEntity> query = mock(TypedQuery.class);
        when(em.createQuery(anyString(), eq(CustomerEntity.class))).thenReturn(query);
        when(query.setParameter(anyString(), any())).thenReturn(query);
        when(query.getResultStream()).thenReturn(List.of(entity).stream());

        Optional<Customer> result = adapter.findByTenantAndCode(TenantId.of(tenantId), "CUST-1");

        assertTrue(result.isPresent());
        assertEquals("CUST-1", result.get().getCustomerCode());
    }

    @Test
    void findByTenantAndCode_ReturnsEmpty_WhenNotFound() {
        TypedQuery<CustomerEntity> query = mock(TypedQuery.class);
        when(em.createQuery(anyString(), eq(CustomerEntity.class))).thenReturn(query);
        when(query.setParameter(anyString(), any())).thenReturn(query);
        when(query.getResultStream()).thenReturn(List.<CustomerEntity>of().stream());

        Optional<Customer> result = adapter.findByTenantAndCode(TenantId.of(UUID.randomUUID()), "NOT-EXIST");

        assertTrue(result.isEmpty());
    }

    @Test
    void existsActive_ReturnsTrue_WhenActiveCustomerExists() {
        UUID id = UUID.randomUUID();
        UUID tenantId = UUID.randomUUID();

        TypedQuery<Long> query = mock(TypedQuery.class);
        when(em.createQuery(anyString(), eq(Long.class))).thenReturn(query);
        when(query.setParameter(anyString(), any())).thenReturn(query);
        when(query.getSingleResult()).thenReturn(1L);

        boolean result = adapter.existsActive(TenantId.of(tenantId), CustomerId.of(id));

        assertTrue(result);
    }

    @Test
    void existsActive_ReturnsFalse_WhenNoActiveCustomer() {
        UUID id = UUID.randomUUID();
        UUID tenantId = UUID.randomUUID();

        TypedQuery<Long> query = mock(TypedQuery.class);
        when(em.createQuery(anyString(), eq(Long.class))).thenReturn(query);
        when(query.setParameter(anyString(), any())).thenReturn(query);
        when(query.getSingleResult()).thenReturn(0L);

        boolean result = adapter.existsActive(TenantId.of(tenantId), CustomerId.of(id));

        assertFalse(result);
    }

    @Test
    void findAllByTenant_ReturnsAllCustomers_WhenIncludeDeletedIsTrue() {
        UUID tenantId = UUID.randomUUID();

        CustomerEntity entity = new CustomerEntity();
        entity.setId(UUID.randomUUID());
        entity.setTenantId(tenantId);
        entity.setCustomerCode("CUST-1");
        entity.setLegalName("Acme");
        entity.setCurrency("USD");
        entity.setStatus(CustomerStatus.DELETED);
        entity.setCreatedAt(Instant.now());
        entity.setUpdatedAt(Instant.now());

        TypedQuery<CustomerEntity> query = mock(TypedQuery.class);
        when(em.createQuery(anyString(), eq(CustomerEntity.class))).thenReturn(query);
        when(query.setParameter(anyString(), any())).thenReturn(query);
        when(query.getResultStream()).thenReturn(List.of(entity).stream());

        List<Customer> result = adapter.findAllByTenant(TenantId.of(tenantId), true);

        assertEquals(1, result.size());
    }

    @Test
    void findAllByTenant_ExcludesDeleted_WhenIncludeDeletedIsFalse() {
        UUID tenantId = UUID.randomUUID();

        CustomerEntity entity = new CustomerEntity();
        entity.setId(UUID.randomUUID());
        entity.setTenantId(tenantId);
        entity.setCustomerCode("CUST-1");
        entity.setLegalName("Acme");
        entity.setCurrency("USD");
        entity.setStatus(CustomerStatus.ACTIVE);
        entity.setCreatedAt(Instant.now());
        entity.setUpdatedAt(Instant.now());

        TypedQuery<CustomerEntity> query = mock(TypedQuery.class);
        when(em.createQuery(anyString(), eq(CustomerEntity.class))).thenReturn(query);
        when(query.setParameter(anyString(), any())).thenReturn(query);
        when(query.getResultStream()).thenReturn(List.of(entity).stream());

        List<Customer> result = adapter.findAllByTenant(TenantId.of(tenantId), false);

        assertEquals(1, result.size());
    }

    @Test
    void findByTenantAndStatus_ReturnsFilteredCustomers() {
        UUID tenantId = UUID.randomUUID();

        CustomerEntity entity = new CustomerEntity();
        entity.setId(UUID.randomUUID());
        entity.setTenantId(tenantId);
        entity.setCustomerCode("CUST-1");
        entity.setLegalName("Acme");
        entity.setCurrency("USD");
        entity.setStatus(CustomerStatus.ACTIVE);
        entity.setCreatedAt(Instant.now());
        entity.setUpdatedAt(Instant.now());

        TypedQuery<CustomerEntity> query = mock(TypedQuery.class);
        when(em.createQuery(anyString(), eq(CustomerEntity.class))).thenReturn(query);
        when(query.setParameter(anyString(), any())).thenReturn(query);
        when(query.getResultStream()).thenReturn(List.of(entity).stream());

        List<Customer> result = adapter.findByTenantAndStatus(TenantId.of(tenantId), CustomerStatus.ACTIVE);

        assertEquals(1, result.size());
    }

    @Test
    void searchByTenant_ReturnsMatchingCustomers() {
        UUID tenantId = UUID.randomUUID();

        CustomerEntity entity = new CustomerEntity();
        entity.setId(UUID.randomUUID());
        entity.setTenantId(tenantId);
        entity.setCustomerCode("CUST-ACME");
        entity.setLegalName("Acme Corporation");
        entity.setDisplayName("Acme");
        entity.setCurrency("USD");
        entity.setStatus(CustomerStatus.ACTIVE);
        entity.setCreatedAt(Instant.now());
        entity.setUpdatedAt(Instant.now());

        TypedQuery<CustomerEntity> query = mock(TypedQuery.class);
        when(em.createQuery(anyString(), eq(CustomerEntity.class))).thenReturn(query);
        when(query.setParameter(anyString(), any())).thenReturn(query);
        when(query.setMaxResults(anyInt())).thenReturn(query);
        when(query.getResultStream()).thenReturn(List.of(entity).stream());

        List<Customer> result = adapter.searchByTenant(TenantId.of(tenantId), "acme");

        assertEquals(1, result.size());
    }

    @Test
    void countByTenantAndStatus_ReturnsCount() {
        UUID tenantId = UUID.randomUUID();

        TypedQuery<Long> query = mock(TypedQuery.class);
        when(em.createQuery(anyString(), eq(Long.class))).thenReturn(query);
        when(query.setParameter(anyString(), any())).thenReturn(query);
        when(query.getSingleResult()).thenReturn(5L);

        long count = adapter.countByTenantAndStatus(TenantId.of(tenantId), CustomerStatus.ACTIVE);

        assertEquals(5L, count);
    }

    @Test
    void existsByTenantAndCode_ReturnsTrue_WhenExists() {
        UUID tenantId = UUID.randomUUID();

        TypedQuery<Long> query = mock(TypedQuery.class);
        when(em.createQuery(anyString(), eq(Long.class))).thenReturn(query);
        when(query.setParameter(anyString(), any())).thenReturn(query);
        when(query.getSingleResult()).thenReturn(1L);

        boolean result = adapter.existsByTenantAndCode(TenantId.of(tenantId), "CUST-1");

        assertTrue(result);
    }

    @Test
    void existsByTenantAndCode_ReturnsFalse_WhenNotExists() {
        UUID tenantId = UUID.randomUUID();

        TypedQuery<Long> query = mock(TypedQuery.class);
        when(em.createQuery(anyString(), eq(Long.class))).thenReturn(query);
        when(query.setParameter(anyString(), any())).thenReturn(query);
        when(query.getSingleResult()).thenReturn(0L);

        boolean result = adapter.existsByTenantAndCode(TenantId.of(tenantId), "NOT-EXIST");

        assertFalse(result);
    }

    @Test
    void delete_SoftDeletesCustomer_WhenFoundAndTenantMatches() {
        UUID id = UUID.randomUUID();
        UUID tenantId = UUID.randomUUID();

        CustomerEntity entity = new CustomerEntity();
        entity.setId(id);
        entity.setTenantId(tenantId);
        entity.setStatus(CustomerStatus.ACTIVE);

        when(em.find(CustomerEntity.class, id)).thenReturn(entity);

        adapter.delete(TenantId.of(tenantId), CustomerId.of(id));

        verify(em).merge(any(CustomerEntity.class));
        assertEquals(CustomerStatus.DELETED, entity.getStatus());
    }

    @Test
    void delete_DoesNothing_WhenNotFound() {
        UUID id = UUID.randomUUID();
        UUID tenantId = UUID.randomUUID();

        when(em.find(CustomerEntity.class, id)).thenReturn(null);

        adapter.delete(TenantId.of(tenantId), CustomerId.of(id));

        verify(em, never()).merge(any());
    }

    @Test
    void delete_DoesNothing_WhenTenantMismatch() {
        UUID id = UUID.randomUUID();
        UUID tenantId = UUID.randomUUID();
        UUID differentTenantId = UUID.randomUUID();

        CustomerEntity entity = new CustomerEntity();
        entity.setId(id);
        entity.setTenantId(differentTenantId);

        when(em.find(CustomerEntity.class, id)).thenReturn(entity);

        adapter.delete(TenantId.of(tenantId), CustomerId.of(id));

        verify(em, never()).merge(any());
    }
}
