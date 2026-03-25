package com.invoicegenie.ar.adapter.persistence.repository;

import com.invoicegenie.ar.adapter.persistence.entity.PaymentAllocationEntity;
import com.invoicegenie.ar.adapter.persistence.entity.PaymentEntity;
import com.invoicegenie.ar.domain.model.customer.CustomerId;
import com.invoicegenie.ar.domain.model.invoice.InvoiceId;
import com.invoicegenie.ar.domain.model.payment.Payment;
import com.invoicegenie.ar.domain.model.payment.PaymentAllocation;
import com.invoicegenie.ar.domain.model.payment.PaymentId;
import com.invoicegenie.ar.domain.model.payment.PaymentMethod;
import com.invoicegenie.shared.domain.Money;
import com.invoicegenie.shared.domain.TenantId;
import jakarta.persistence.EntityManager;
import jakarta.persistence.Query;
import jakarta.persistence.TypedQuery;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class PaymentRepositoryAdapterTest {

    private PaymentRepositoryAdapter adapter;
    private EntityManager em;

    @BeforeEach
    void setUp() {
        adapter = new PaymentRepositoryAdapter();
        em = mock(EntityManager.class);
        adapter.em = em;
    }

    @Test
    void savePersistsAllocations() {
        Query deleteQuery = mock(Query.class);
        when(em.createQuery(anyString())).thenReturn(deleteQuery);
        when(deleteQuery.setParameter(anyString(), any())).thenReturn(deleteQuery);
        when(deleteQuery.executeUpdate()).thenReturn(1);

        Payment payment = new Payment(PaymentId.of(UUID.randomUUID()), "PAY-1", CustomerId.of(UUID.randomUUID()),
                Money.of("500.00", "USD"), LocalDate.of(2026, 3, 5), PaymentMethod.BANK_TRANSFER);
        payment.allocate(InvoiceId.of(UUID.randomUUID()), Money.of("100.00", "USD"), null, "partial");

        adapter.save(TenantId.of(UUID.randomUUID()), payment);

        verify(em).merge(any(PaymentEntity.class));
        verify(em).persist(any(PaymentAllocationEntity.class));
        verify(deleteQuery).executeUpdate();
    }

    @Test
    void findByTenantAndId_ReturnsEmpty_WhenNotFound() {
        UUID id = UUID.randomUUID();
        when(em.find(PaymentEntity.class, id)).thenReturn(null);

        Optional<Payment> result = adapter.findByTenantAndId(TenantId.of(UUID.randomUUID()), PaymentId.of(id));

        assertTrue(result.isEmpty());
    }

    @Test
    void findByTenantAndId_ReturnsEmpty_ForDifferentTenant() {
        UUID id = UUID.randomUUID();
        UUID tenantId = UUID.randomUUID();
        UUID differentTenantId = UUID.randomUUID();

        PaymentEntity entity = new PaymentEntity();
        entity.setId(id);
        entity.setTenantId(differentTenantId);

        when(em.find(PaymentEntity.class, id)).thenReturn(entity);

        Optional<Payment> result = adapter.findByTenantAndId(TenantId.of(tenantId), PaymentId.of(id));

        assertTrue(result.isEmpty());
    }

    @Test
    void findByTenantAndNumber_ReturnsEmpty_WhenNotFound() {
        TypedQuery<PaymentEntity> query = mock(TypedQuery.class);
        when(em.createQuery(anyString(), eq(PaymentEntity.class))).thenReturn(query);
        when(query.setParameter(anyString(), any())).thenReturn(query);
        when(query.getResultStream()).thenReturn(List.<PaymentEntity>of().stream());

        Optional<Payment> result = adapter.findByTenantAndNumber(TenantId.of(UUID.randomUUID()), "NOT-EXIST");

        assertTrue(result.isEmpty());
    }

    @Test
    void findUnallocatedByTenantAndCustomer_ReturnsEmpty_WhenNotFound() {
        TypedQuery<PaymentEntity> query = mock(TypedQuery.class);
        when(em.createQuery(anyString(), eq(PaymentEntity.class))).thenReturn(query);
        when(query.setParameter(anyString(), any())).thenReturn(query);
        when(query.getResultList()).thenReturn(List.of());

        List<Payment> result = adapter.findUnallocatedByTenantAndCustomer(TenantId.of(UUID.randomUUID()), CustomerId.of(UUID.randomUUID()));

        assertTrue(result.isEmpty());
    }

    @Test
    void findAllocationsByTenantAndInvoice_ReturnsAllocations() {
        UUID tenantId = UUID.randomUUID();
        UUID invoiceId = UUID.randomUUID();
        UUID paymentId = UUID.randomUUID();
        Instant now = Instant.now();

        PaymentAllocationEntity entity = new PaymentAllocationEntity();
        entity.setId(UUID.randomUUID());
        entity.setTenantId(tenantId);
        entity.setPaymentId(paymentId);
        entity.setInvoiceId(invoiceId);
        entity.setAmount(new BigDecimal("100.00"));
        entity.setCurrency("USD");
        entity.setAllocatedAt(now);
        entity.setAllocatedBy(UUID.randomUUID());
        entity.setNotes("Partial payment");

        TypedQuery<PaymentAllocationEntity> query = mock(TypedQuery.class);
        when(em.createQuery(contains("PaymentAllocationEntity"), eq(PaymentAllocationEntity.class))).thenReturn(query);
        when(query.setParameter(anyString(), any())).thenReturn(query);
        when(query.getResultStream()).thenReturn(List.of(entity).stream());

        List<PaymentAllocation> result = adapter.findAllocationsByTenantAndInvoice(TenantId.of(tenantId), InvoiceId.of(invoiceId));

        assertEquals(1, result.size());
        assertEquals(new BigDecimal("100.00"), result.get(0).getAmount().getAmount());
    }
}
