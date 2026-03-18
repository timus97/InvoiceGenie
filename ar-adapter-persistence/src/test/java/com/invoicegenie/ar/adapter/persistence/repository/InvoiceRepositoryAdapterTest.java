package com.invoicegenie.ar.adapter.persistence.repository;

import com.invoicegenie.ar.adapter.persistence.entity.InvoiceEntity;
import com.invoicegenie.ar.adapter.persistence.entity.InvoiceLineEntity;
import com.invoicegenie.ar.domain.model.invoice.Invoice;
import com.invoicegenie.ar.domain.model.invoice.InvoiceId;
import com.invoicegenie.ar.domain.model.invoice.InvoiceLine;
import com.invoicegenie.shared.domain.Money;
import com.invoicegenie.shared.domain.TenantId;
import jakarta.persistence.EntityManager;
import jakarta.persistence.Query;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class InvoiceRepositoryAdapterTest {

    @Test
    void savePersistsInvoiceAndLines() {
        EntityManager em = mock(EntityManager.class);
        Query deleteQuery = mock(Query.class);
        when(em.createQuery(anyString())).thenReturn(deleteQuery);
        when(deleteQuery.setParameter(anyString(), any())).thenReturn(deleteQuery);
        when(deleteQuery.executeUpdate()).thenReturn(1);

        InvoiceRepositoryAdapter adapter = new InvoiceRepositoryAdapter();
        adapter.em = em;

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
    void findByTenantAndIdReturnsEmptyForDifferentTenant() {
        EntityManager em = mock(EntityManager.class);
        InvoiceRepositoryAdapter adapter = new InvoiceRepositoryAdapter();
        adapter.em = em;

        UUID invoiceUuid = UUID.randomUUID();
        InvoiceEntity entity = new InvoiceEntity();
        entity.setId(invoiceUuid);
        entity.setTenantId(UUID.randomUUID());
        when(em.find(InvoiceEntity.class, invoiceUuid)).thenReturn(entity);

        Optional<Invoice> result = adapter.findByTenantAndId(TenantId.of(UUID.randomUUID()), InvoiceId.of(invoiceUuid));
        assertTrue(result.isEmpty());
    }
}
