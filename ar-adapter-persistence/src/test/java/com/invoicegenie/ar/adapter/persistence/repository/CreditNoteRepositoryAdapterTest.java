package com.invoicegenie.ar.adapter.persistence.repository;

import com.invoicegenie.ar.adapter.persistence.entity.CreditNoteEntity;
import com.invoicegenie.ar.domain.model.customer.CustomerId;
import com.invoicegenie.ar.domain.model.payment.CreditNote;
import com.invoicegenie.shared.domain.Money;
import com.invoicegenie.shared.domain.TenantId;
import jakarta.persistence.EntityManager;
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

class CreditNoteRepositoryAdapterTest {

    private CreditNoteRepositoryAdapter adapter;
    private EntityManager em;

    @BeforeEach
    void setUp() {
        adapter = new CreditNoteRepositoryAdapter();
        em = mock(EntityManager.class);
        adapter.em = em;
    }

    @Test
    void saveMergesEntity() {
        UUID id = UUID.randomUUID();
        UUID customerId = UUID.randomUUID();
        Instant now = Instant.now();
        LocalDate today = LocalDate.now();

        CreditNote creditNote = new CreditNote(
            id, "CN-001", CustomerId.of(customerId), Money.of("50.00", "USD"),
            CreditNote.CreditNoteType.EARLY_PAYMENT_DISCOUNT, null, "Test",
            CreditNote.CreditNoteStatus.ISSUED, today, null, null, null, null, now, now
        );

        adapter.save(TenantId.of(UUID.randomUUID()), creditNote);

        verify(em).merge(any(CreditNoteEntity.class));
    }

    @Test
    void findByTenantAndId_ReturnsCreditNote_WhenFoundAndTenantMatches() {
        UUID id = UUID.randomUUID();
        UUID tenantId = UUID.randomUUID();
        UUID customerId = UUID.randomUUID();
        Instant now = Instant.now();
        LocalDate today = LocalDate.now();

        CreditNoteEntity entity = new CreditNoteEntity();
        entity.setId(id);
        entity.setTenantId(tenantId);
        entity.setCreditNoteNumber("CN-001");
        entity.setCustomerId(customerId);
        entity.setAmount(new BigDecimal("50.00"));
        entity.setCurrency("USD");
        entity.setType(CreditNoteEntity.CreditNoteType.EARLY_PAYMENT_DISCOUNT);
        entity.setStatus(CreditNoteEntity.CreditNoteStatus.ISSUED);
        entity.setIssueDate(today);
        entity.setCreatedAt(now);
        entity.setUpdatedAt(now);

        when(em.find(CreditNoteEntity.class, id)).thenReturn(entity);

        Optional<CreditNote> result = adapter.findByTenantAndId(TenantId.of(tenantId), id);

        assertTrue(result.isPresent());
        assertEquals(id, result.get().getId());
        assertEquals("CN-001", result.get().getCreditNoteNumber());
    }

    @Test
    void findByTenantAndId_ReturnsEmpty_WhenNotFound() {
        UUID id = UUID.randomUUID();
        when(em.find(CreditNoteEntity.class, id)).thenReturn(null);

        Optional<CreditNote> result = adapter.findByTenantAndId(TenantId.of(UUID.randomUUID()), id);

        assertTrue(result.isEmpty());
    }

    @Test
    void findByTenantAndId_ReturnsEmpty_WhenTenantMismatch() {
        UUID id = UUID.randomUUID();
        UUID tenantId = UUID.randomUUID();
        UUID differentTenantId = UUID.randomUUID();

        CreditNoteEntity entity = new CreditNoteEntity();
        entity.setId(id);
        entity.setTenantId(differentTenantId);

        when(em.find(CreditNoteEntity.class, id)).thenReturn(entity);

        Optional<CreditNote> result = adapter.findByTenantAndId(TenantId.of(tenantId), id);

        assertTrue(result.isEmpty());
    }

    @Test
    void findByTenantAndCustomer_ReturnsListOfCreditNotes() {
        UUID tenantId = UUID.randomUUID();
        UUID customerId = UUID.randomUUID();
        Instant now = Instant.now();
        LocalDate today = LocalDate.now();

        CreditNoteEntity entity = new CreditNoteEntity();
        entity.setId(UUID.randomUUID());
        entity.setTenantId(tenantId);
        entity.setCreditNoteNumber("CN-001");
        entity.setCustomerId(customerId);
        entity.setAmount(new BigDecimal("25.00"));
        entity.setCurrency("USD");
        entity.setType(CreditNoteEntity.CreditNoteType.ADJUSTMENT);
        entity.setStatus(CreditNoteEntity.CreditNoteStatus.ISSUED);
        entity.setIssueDate(today);
        entity.setCreatedAt(now);
        entity.setUpdatedAt(now);

        TypedQuery<CreditNoteEntity> query = mock(TypedQuery.class);
        when(em.createQuery(anyString(), eq(CreditNoteEntity.class))).thenReturn(query);
        when(query.setParameter(anyString(), any())).thenReturn(query);
        when(query.getResultStream()).thenReturn(List.of(entity).stream());

        List<CreditNote> result = adapter.findByTenantAndCustomer(TenantId.of(tenantId), CustomerId.of(customerId));

        assertEquals(1, result.size());
    }

    @Test
    void findByTenantAndStatus_ReturnsListOfCreditNotes() {
        UUID tenantId = UUID.randomUUID();
        UUID customerId = UUID.randomUUID();
        Instant now = Instant.now();
        LocalDate today = LocalDate.now();

        CreditNoteEntity entity = new CreditNoteEntity();
        entity.setId(UUID.randomUUID());
        entity.setTenantId(tenantId);
        entity.setCreditNoteNumber("CN-001");
        entity.setCustomerId(customerId);
        entity.setAmount(new BigDecimal("30.00"));
        entity.setCurrency("USD");
        entity.setType(CreditNoteEntity.CreditNoteType.REFUND);
        entity.setStatus(CreditNoteEntity.CreditNoteStatus.APPLIED);
        entity.setIssueDate(today);
        entity.setCreatedAt(now);
        entity.setUpdatedAt(now);

        TypedQuery<CreditNoteEntity> query = mock(TypedQuery.class);
        when(em.createQuery(anyString(), eq(CreditNoteEntity.class))).thenReturn(query);
        when(query.setParameter(anyString(), any())).thenReturn(query);
        when(query.getResultStream()).thenReturn(List.of(entity).stream());

        List<CreditNote> result = adapter.findByTenantAndStatus(TenantId.of(tenantId), CreditNote.CreditNoteStatus.APPLIED);

        assertEquals(1, result.size());
        assertEquals(CreditNote.CreditNoteStatus.APPLIED, result.get(0).getStatus());
    }

    @Test
    void findAvailableByTenantAndCustomer_ReturnsAvailableCreditNotes() {
        UUID tenantId = UUID.randomUUID();
        UUID customerId = UUID.randomUUID();
        Instant now = Instant.now();
        LocalDate today = LocalDate.now();

        CreditNoteEntity entity = new CreditNoteEntity();
        entity.setId(UUID.randomUUID());
        entity.setTenantId(tenantId);
        entity.setCreditNoteNumber("CN-AVAIL");
        entity.setCustomerId(customerId);
        entity.setAmount(new BigDecimal("40.00"));
        entity.setCurrency("USD");
        entity.setType(CreditNoteEntity.CreditNoteType.EARLY_PAYMENT_DISCOUNT);
        entity.setStatus(CreditNoteEntity.CreditNoteStatus.ISSUED);
        entity.setIssueDate(today);
        entity.setExpiryDate(today.plusYears(1));
        entity.setCreatedAt(now);
        entity.setUpdatedAt(now);

        TypedQuery<CreditNoteEntity> query = mock(TypedQuery.class);
        when(em.createQuery(anyString(), eq(CreditNoteEntity.class))).thenReturn(query);
        when(query.setParameter(anyString(), any())).thenReturn(query);
        when(query.getResultStream()).thenReturn(List.of(entity).stream());

        List<CreditNote> result = adapter.findAvailableByTenantAndCustomer(TenantId.of(tenantId), CustomerId.of(customerId));

        assertEquals(1, result.size());
        assertEquals(CreditNote.CreditNoteStatus.ISSUED, result.get(0).getStatus());
    }
}
