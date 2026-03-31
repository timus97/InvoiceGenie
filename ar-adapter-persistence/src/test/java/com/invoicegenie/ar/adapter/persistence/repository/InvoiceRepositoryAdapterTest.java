package com.invoicegenie.ar.adapter.persistence.repository;

import com.invoicegenie.ar.adapter.persistence.entity.InvoiceEntity;
import com.invoicegenie.ar.adapter.persistence.entity.InvoiceLineEntity;
import com.invoicegenie.ar.domain.model.customer.CustomerId;
import com.invoicegenie.ar.domain.model.invoice.Invoice;
import com.invoicegenie.ar.domain.model.invoice.InvoiceId;
import com.invoicegenie.ar.domain.model.invoice.InvoiceLine;
import com.invoicegenie.ar.domain.model.invoice.InvoiceRepository;
import com.invoicegenie.ar.domain.model.invoice.InvoiceStatus;
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
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class InvoiceRepositoryAdapterTest {

    private InvoiceRepositoryAdapter adapter;
    private EntityManager em;

    @BeforeEach
    void setUp() {
        adapter = new InvoiceRepositoryAdapter();
        em = mock(EntityManager.class);
        adapter.em = em;
    }

    @Test
    void savePersistsInvoiceAndLines() {
        Query deleteQuery = mock(Query.class);
        when(em.createQuery(anyString())).thenReturn(deleteQuery);
        when(deleteQuery.setParameter(anyString(), any())).thenReturn(deleteQuery);
        when(deleteQuery.executeUpdate()).thenReturn(1);

        TenantId tenantId = TenantId.of(UUID.randomUUID());
        InvoiceId invoiceId = InvoiceId.of(UUID.randomUUID());
        InvoiceLine line = new InvoiceLine(1, "Design", Money.of("100.00", "USD"));
        Invoice invoice = new Invoice(invoiceId, "INV-100", "CUST-1", "USD",
                LocalDate.of(2026, 3, 1), LocalDate.of(2026, 3, 31), List.of(line));
        invoice.issue();
        invoice.markOverdue(LocalDate.of(2026, 4, 10));

        adapter.save(tenantId, invoice);

        verify(em).merge(any(InvoiceEntity.class));
        verify(em).persist(any(InvoiceLineEntity.class));
        verify(deleteQuery).executeUpdate();
    }

    @Test
    void findByTenantAndId_ReturnsInvoice_WhenFoundAndTenantMatches() {
        UUID invoiceUuid = UUID.randomUUID();
        UUID tenantId = UUID.randomUUID();
        Instant now = Instant.now();

        InvoiceEntity entity = new InvoiceEntity();
        entity.setId(invoiceUuid);
        entity.setTenantId(tenantId);
        entity.setInvoiceNumber("INV-100");
        entity.setCustomerRef("CUST-1");
        entity.setCurrencyCode("USD");
        entity.setStatus(InvoiceStatus.ISSUED);
        entity.setIssueDate(LocalDate.now());
        entity.setDueDate(LocalDate.now().plusDays(30));
        entity.setTotal(new BigDecimal("100.00"));
        entity.setCreatedAt(now);
        entity.setUpdatedAt(now);

        TypedQuery<InvoiceLineEntity> lineQuery = mock(TypedQuery.class);
        when(em.createQuery(contains("InvoiceLineEntity"), eq(InvoiceLineEntity.class))).thenReturn(lineQuery);
        when(lineQuery.setParameter(anyString(), any())).thenReturn(lineQuery);
        when(lineQuery.getResultList()).thenReturn(List.of());

        when(em.find(InvoiceEntity.class, invoiceUuid)).thenReturn(entity);

        Optional<Invoice> result = adapter.findByTenantAndId(TenantId.of(tenantId), InvoiceId.of(invoiceUuid));

        assertTrue(result.isPresent());
        assertEquals("INV-100", result.get().getInvoiceNumber());
    }

    @Test
    void findByTenantAndId_ReturnsEmpty_WhenNotFound() {
        UUID invoiceUuid = UUID.randomUUID();
        when(em.find(InvoiceEntity.class, invoiceUuid)).thenReturn(null);

        Optional<Invoice> result = adapter.findByTenantAndId(TenantId.of(UUID.randomUUID()), InvoiceId.of(invoiceUuid));

        assertTrue(result.isEmpty());
    }

    @Test
    void findByTenantAndId_ReturnsEmpty_ForDifferentTenant() {
        UUID invoiceUuid = UUID.randomUUID();
        UUID tenantId = UUID.randomUUID();
        UUID differentTenantId = UUID.randomUUID();

        InvoiceEntity entity = new InvoiceEntity();
        entity.setId(invoiceUuid);
        entity.setTenantId(differentTenantId);

        when(em.find(InvoiceEntity.class, invoiceUuid)).thenReturn(entity);

        Optional<Invoice> result = adapter.findByTenantAndId(TenantId.of(tenantId), InvoiceId.of(invoiceUuid));

        assertTrue(result.isEmpty());
    }

    @Test
    void findByTenant_ReturnsPagedResults() {
        UUID tenantId = UUID.randomUUID();

        TypedQuery<InvoiceEntity> query = mock(TypedQuery.class);
        when(em.createQuery(anyString(), eq(InvoiceEntity.class))).thenReturn(query);
        when(query.setParameter(anyString(), any())).thenReturn(query);
        when(query.setMaxResults(anyInt())).thenReturn(query);
        when(query.getResultList()).thenReturn(List.of());

        InvoiceRepository.Page result = adapter.findByTenant(TenantId.of(tenantId), 10, null);

        assertTrue(result.items().isEmpty());
        assertFalse(result.nextCursor().isPresent());
    }

    @Test
    void findByTenant_ReturnsPagedResults_WithCursor() {
        UUID tenantId = UUID.randomUUID();
        UUID invoiceId = UUID.randomUUID();
        Instant now = Instant.now();

        TypedQuery<InvoiceEntity> query = mock(TypedQuery.class);
        when(em.createQuery(anyString(), eq(InvoiceEntity.class))).thenReturn(query);
        when(query.setParameter(anyString(), any())).thenReturn(query);
        when(query.setMaxResults(anyInt())).thenReturn(query);
        when(query.getResultList()).thenReturn(List.of());

        InvoiceRepository.PageCursor cursor = new InvoiceRepository.PageCursor(now, InvoiceId.of(invoiceId));
        InvoiceRepository.Page result = adapter.findByTenant(TenantId.of(tenantId), 10, cursor);

        assertTrue(result.items().isEmpty());
    }

    @Test
    void findOpenByTenantAndCustomer_ReturnsOpenInvoices() {
        UUID tenantId = UUID.randomUUID();
        UUID customerId = UUID.randomUUID();

        TypedQuery<InvoiceEntity> query = mock(TypedQuery.class);
        when(em.createQuery(contains("InvoiceEntity"), eq(InvoiceEntity.class))).thenReturn(query);
        when(query.setParameter(anyString(), any())).thenReturn(query);
        when(query.getResultList()).thenReturn(List.of());

        List<Invoice> result = adapter.findOpenByTenantAndCustomer(TenantId.of(tenantId), CustomerId.of(customerId));

        assertTrue(result.isEmpty());
    }
}
