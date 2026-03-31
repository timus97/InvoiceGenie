package com.invoicegenie.ar.adapter.persistence.repository;

import com.invoicegenie.ar.adapter.persistence.entity.ChequeEntity;
import com.invoicegenie.ar.domain.model.customer.CustomerId;
import com.invoicegenie.ar.domain.model.payment.Cheque;
import com.invoicegenie.ar.domain.model.payment.ChequeStatus;
import com.invoicegenie.shared.domain.Money;
import com.invoicegenie.shared.domain.TenantId;
import jakarta.persistence.EntityManager;
import jakarta.persistence.TypedQuery;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class ChequeRepositoryAdapterTest {

    private ChequeRepositoryAdapter adapter;
    private EntityManager em;

    @BeforeEach
    void setUp() {
        adapter = new ChequeRepositoryAdapter();
        em = mock(EntityManager.class);
        adapter.em = em;
    }

    @Test
    void saveMergesEntity() {
        UUID id = UUID.randomUUID();
        UUID customerId = UUID.randomUUID();
        Instant now = Instant.now();
        LocalDate today = LocalDate.now();

        Cheque cheque = new Cheque(
            id, "CHQ-001", CustomerId.of(customerId), Money.of("100.00", "USD"),
            "Bank", "Branch", today, today, null, null, null, null,
            ChequeStatus.RECEIVED, null, new ArrayList<>(), "Notes", now, now
        );

        adapter.save(TenantId.of(UUID.randomUUID()), cheque);

        verify(em).merge(any(ChequeEntity.class));
    }

    @Test
    void findByTenantAndId_ReturnsCheque_WhenFoundAndTenantMatches() {
        UUID id = UUID.randomUUID();
        UUID tenantId = UUID.randomUUID();
        UUID customerId = UUID.randomUUID();
        Instant now = Instant.now();
        LocalDate today = LocalDate.now();

        ChequeEntity entity = new ChequeEntity();
        entity.setId(id);
        entity.setTenantId(tenantId);
        entity.setChequeNumber("CHQ-001");
        entity.setCustomerId(customerId);
        entity.setAmount(new BigDecimal("100.00"));
        entity.setCurrency("USD");
        entity.setBankName("Bank");
        entity.setBankBranch("Branch");
        entity.setChequeDate(today);
        entity.setReceivedDate(today);
        entity.setStatus(ChequeStatus.RECEIVED);
        entity.setCreatedAt(now);
        entity.setUpdatedAt(now);

        when(em.find(ChequeEntity.class, id)).thenReturn(entity);

        Optional<Cheque> result = adapter.findByTenantAndId(TenantId.of(tenantId), id);

        assertTrue(result.isPresent());
        assertEquals(id, result.get().getId());
        assertEquals("CHQ-001", result.get().getChequeNumber());
    }

    @Test
    void findByTenantAndId_ReturnsEmpty_WhenNotFound() {
        UUID id = UUID.randomUUID();
        when(em.find(ChequeEntity.class, id)).thenReturn(null);

        Optional<Cheque> result = adapter.findByTenantAndId(TenantId.of(UUID.randomUUID()), id);

        assertTrue(result.isEmpty());
    }

    @Test
    void findByTenantAndId_ReturnsEmpty_WhenTenantMismatch() {
        UUID id = UUID.randomUUID();
        UUID tenantId = UUID.randomUUID();
        UUID differentTenantId = UUID.randomUUID();

        ChequeEntity entity = new ChequeEntity();
        entity.setId(id);
        entity.setTenantId(differentTenantId);

        when(em.find(ChequeEntity.class, id)).thenReturn(entity);

        Optional<Cheque> result = adapter.findByTenantAndId(TenantId.of(tenantId), id);

        assertTrue(result.isEmpty());
    }

    @Test
    void findByTenantAndChequeNumber_ReturnsCheque_WhenFound() {
        UUID id = UUID.randomUUID();
        UUID tenantId = UUID.randomUUID();
        UUID customerId = UUID.randomUUID();
        Instant now = Instant.now();
        LocalDate today = LocalDate.now();

        ChequeEntity entity = new ChequeEntity();
        entity.setId(id);
        entity.setTenantId(tenantId);
        entity.setChequeNumber("CHQ-001");
        entity.setCustomerId(customerId);
        entity.setAmount(new BigDecimal("200.00"));
        entity.setCurrency("USD");
        entity.setBankName("Bank");
        entity.setChequeDate(today);
        entity.setReceivedDate(today);
        entity.setStatus(ChequeStatus.RECEIVED);
        entity.setCreatedAt(now);
        entity.setUpdatedAt(now);

        TypedQuery<ChequeEntity> query = mock(TypedQuery.class);
        when(em.createQuery(anyString(), eq(ChequeEntity.class))).thenReturn(query);
        when(query.setParameter(anyString(), any())).thenReturn(query);
        when(query.getResultStream()).thenReturn(List.of(entity).stream());

        Optional<Cheque> result = adapter.findByTenantAndChequeNumber(TenantId.of(tenantId), "CHQ-001");

        assertTrue(result.isPresent());
        assertEquals("CHQ-001", result.get().getChequeNumber());
    }

    @Test
    void findByTenantAndChequeNumber_ReturnsEmpty_WhenNotFound() {
        TypedQuery<ChequeEntity> query = mock(TypedQuery.class);
        when(em.createQuery(anyString(), eq(ChequeEntity.class))).thenReturn(query);
        when(query.setParameter(anyString(), any())).thenReturn(query);
        when(query.getResultStream()).thenReturn(List.<ChequeEntity>of().stream());

        Optional<Cheque> result = adapter.findByTenantAndChequeNumber(TenantId.of(UUID.randomUUID()), "NOT-EXIST");

        assertTrue(result.isEmpty());
    }

    @Test
    void findByTenantAndCustomer_ReturnsListOfCheques() {
        UUID tenantId = UUID.randomUUID();
        UUID customerId = UUID.randomUUID();
        Instant now = Instant.now();
        LocalDate today = LocalDate.now();

        ChequeEntity entity1 = new ChequeEntity();
        entity1.setId(UUID.randomUUID());
        entity1.setTenantId(tenantId);
        entity1.setChequeNumber("CHQ-001");
        entity1.setCustomerId(customerId);
        entity1.setAmount(new BigDecimal("100.00"));
        entity1.setCurrency("USD");
        entity1.setBankName("Bank");
        entity1.setChequeDate(today);
        entity1.setReceivedDate(today);
        entity1.setStatus(ChequeStatus.RECEIVED);
        entity1.setCreatedAt(now);
        entity1.setUpdatedAt(now);

        ChequeEntity entity2 = new ChequeEntity();
        entity2.setId(UUID.randomUUID());
        entity2.setTenantId(tenantId);
        entity2.setChequeNumber("CHQ-002");
        entity2.setCustomerId(customerId);
        entity2.setAmount(new BigDecimal("200.00"));
        entity2.setCurrency("USD");
        entity2.setBankName("Bank");
        entity2.setChequeDate(today);
        entity2.setReceivedDate(today);
        entity2.setStatus(ChequeStatus.CLEARED);
        entity2.setCreatedAt(now);
        entity2.setUpdatedAt(now);

        TypedQuery<ChequeEntity> query = mock(TypedQuery.class);
        when(em.createQuery(anyString(), eq(ChequeEntity.class))).thenReturn(query);
        when(query.setParameter(anyString(), any())).thenReturn(query);
        when(query.getResultStream()).thenReturn(List.of(entity1, entity2).stream());

        List<Cheque> result = adapter.findByTenantAndCustomer(TenantId.of(tenantId), CustomerId.of(customerId));

        assertEquals(2, result.size());
    }

    @Test
    void findByTenantAndStatus_ReturnsListOfCheques() {
        UUID tenantId = UUID.randomUUID();
        UUID customerId = UUID.randomUUID();
        Instant now = Instant.now();
        LocalDate today = LocalDate.now();

        ChequeEntity entity = new ChequeEntity();
        entity.setId(UUID.randomUUID());
        entity.setTenantId(tenantId);
        entity.setChequeNumber("CHQ-001");
        entity.setCustomerId(customerId);
        entity.setAmount(new BigDecimal("100.00"));
        entity.setCurrency("USD");
        entity.setBankName("Bank");
        entity.setChequeDate(today);
        entity.setReceivedDate(today);
        entity.setStatus(ChequeStatus.DEPOSITED);
        entity.setCreatedAt(now);
        entity.setUpdatedAt(now);

        TypedQuery<ChequeEntity> query = mock(TypedQuery.class);
        when(em.createQuery(anyString(), eq(ChequeEntity.class))).thenReturn(query);
        when(query.setParameter(anyString(), any())).thenReturn(query);
        when(query.getResultStream()).thenReturn(List.of(entity).stream());

        List<Cheque> result = adapter.findByTenantAndStatus(TenantId.of(tenantId), ChequeStatus.DEPOSITED);

        assertEquals(1, result.size());
        assertEquals(ChequeStatus.DEPOSITED, result.get(0).getStatus());
    }
}
