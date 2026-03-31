package com.invoicegenie.ar.adapter.persistence.mapper;

import com.invoicegenie.ar.adapter.persistence.entity.InvoiceEntity;
import com.invoicegenie.ar.adapter.persistence.entity.InvoiceLineEntity;
import com.invoicegenie.ar.domain.model.invoice.Invoice;
import com.invoicegenie.ar.domain.model.invoice.InvoiceId;
import com.invoicegenie.ar.domain.model.invoice.InvoiceLine;
import com.invoicegenie.ar.domain.model.invoice.InvoiceStatus;
import com.invoicegenie.shared.domain.Money;
import com.invoicegenie.shared.domain.TenantId;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;

class InvoiceMapperTest {

    @Test
    void mapsInvoiceToEntityAndBack() {
        TenantId tenantId = TenantId.of(UUID.randomUUID());
        InvoiceId invoiceId = InvoiceId.of(UUID.randomUUID());
        InvoiceLine line = new InvoiceLine(1, "Design", new BigDecimal("2"),
                Money.of("100.00", "USD"), Money.of("10.00", "USD"),
                new BigDecimal("0.10"), Money.of("19.00", "USD"), Money.of("209.00", "USD"));

        Invoice invoice = new Invoice(invoiceId, "INV-001", "CUST-1", "USD",
                LocalDate.of(2026, 3, 1), LocalDate.of(2026, 3, 31), List.of(line));
        invoice.issue();
        invoice.markOverdue(LocalDate.of(2026, 4, 10));
        invoice.writeOff("bad debt");

        InvoiceMapper mapper = new InvoiceMapper();
        InvoiceEntity entity = mapper.toEntity(tenantId, invoice);
        List<InvoiceLineEntity> lineEntities = mapper.toLineEntities(tenantId, invoice);

        Invoice restored = mapper.toDomain(entity, lineEntities);
        assertEquals(invoice.getInvoiceNumber(), restored.getInvoiceNumber());
        assertEquals(invoice.getCurrencyCode(), restored.getCurrencyCode());
        assertEquals(invoice.getLines().size(), restored.getLines().size());
        assertEquals(invoice.getTotal().getAmount(), restored.getTotal().getAmount());
    }
}
