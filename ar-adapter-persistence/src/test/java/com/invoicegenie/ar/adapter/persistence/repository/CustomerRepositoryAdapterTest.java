package com.invoicegenie.ar.adapter.persistence.repository;

import com.invoicegenie.ar.adapter.persistence.entity.CustomerEntity;
import com.invoicegenie.ar.domain.model.customer.Customer;
import com.invoicegenie.ar.domain.model.customer.CustomerId;
import com.invoicegenie.shared.domain.TenantId;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class CustomerRepositoryAdapterTest {

    @Test
    void saveMergesEntity() {
        EntityManager em = mock(EntityManager.class);
        CustomerRepositoryAdapter adapter = new CustomerRepositoryAdapter();
        adapter.em = em;

        Customer customer = new Customer(CustomerId.of(UUID.randomUUID()), "CUST-1", "Acme", "USD");
        adapter.save(TenantId.of(UUID.randomUUID()), customer);

        verify(em).merge(any(CustomerEntity.class));
    }

    @Test
    void findByTenantAndIdReturnsEmptyForDifferentTenant() {
        EntityManager em = mock(EntityManager.class);
        CustomerRepositoryAdapter adapter = new CustomerRepositoryAdapter();
        adapter.em = em;

        UUID id = UUID.randomUUID();
        CustomerEntity entity = new CustomerEntity();
        entity.setId(id);
        entity.setTenantId(UUID.randomUUID());
        when(em.find(CustomerEntity.class, id)).thenReturn(entity);

        Optional<Customer> result = adapter.findByTenantAndId(TenantId.of(UUID.randomUUID()), CustomerId.of(id));
        assertTrue(result.isEmpty());
    }
}
