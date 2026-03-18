package com.invoicegenie.ar.adapter.persistence.repository;

import com.invoicegenie.ar.adapter.persistence.entity.PaymentAllocationEntity;
import com.invoicegenie.ar.adapter.persistence.entity.PaymentEntity;
import com.invoicegenie.ar.domain.model.customer.CustomerId;
import com.invoicegenie.ar.domain.model.invoice.InvoiceId;
import com.invoicegenie.ar.domain.model.payment.Payment;
import com.invoicegenie.ar.domain.model.payment.PaymentId;
import com.invoicegenie.ar.domain.model.payment.PaymentMethod;
import com.invoicegenie.shared.domain.Money;
import com.invoicegenie.shared.domain.TenantId;
import jakarta.persistence.EntityManager;
import jakarta.persistence.Query;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class PaymentRepositoryAdapterTest {

    @Test
    void savePersistsAllocations() {
        EntityManager em = mock(EntityManager.class);
        Query deleteQuery = mock(Query.class);
        when(em.createQuery(anyString())).thenReturn(deleteQuery);
        when(deleteQuery.setParameter(anyString(), any())).thenReturn(deleteQuery);
        when(deleteQuery.executeUpdate()).thenReturn(1);

        PaymentRepositoryAdapter adapter = new PaymentRepositoryAdapter();
        adapter.em = em;

        Payment payment = new Payment(PaymentId.of(UUID.randomUUID()), "PAY-1", CustomerId.of(UUID.randomUUID()),
                Money.of("500.00", "USD"), LocalDate.of(2026, 3, 5), PaymentMethod.BANK_TRANSFER);
        payment.allocate(InvoiceId.of(UUID.randomUUID()), Money.of("100.00", "USD"), null, "partial");

        adapter.save(TenantId.of(UUID.randomUUID()), payment);

        verify(em).merge(any(PaymentEntity.class));
        verify(em).persist(any(PaymentAllocationEntity.class));
        verify(deleteQuery).executeUpdate();
    }

    @Test
    void findByTenantAndIdReturnsEmptyForDifferentTenant() {
        EntityManager em = mock(EntityManager.class);
        PaymentRepositoryAdapter adapter = new PaymentRepositoryAdapter();
        adapter.em = em;

        UUID id = UUID.randomUUID();
        PaymentEntity entity = new PaymentEntity();
        entity.setId(id);
        entity.setTenantId(UUID.randomUUID());
        when(em.find(PaymentEntity.class, id)).thenReturn(entity);

        Optional<Payment> result = adapter.findByTenantAndId(TenantId.of(UUID.randomUUID()), PaymentId.of(id));
        assertTrue(result.isEmpty());
    }
}
