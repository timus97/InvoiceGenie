package com.invoicegenie.ar.domain.service;

import com.invoicegenie.ar.domain.model.customer.CustomerId;
import com.invoicegenie.ar.domain.model.invoice.Invoice;
import com.invoicegenie.ar.domain.model.invoice.InvoiceId;
import com.invoicegenie.ar.domain.model.invoice.InvoiceLine;
import com.invoicegenie.shared.domain.Money;
import com.invoicegenie.shared.domain.TenantId;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

class InvoiceSnapshotServiceTest {

    @Test
    void buildsJsonSnapshot() {
        InvoiceId id = InvoiceId.generate();
        Invoice inv = new Invoice(id, "INV-1", CustomerId.of(UUID.randomUUID()), "Cust",
                "USD", LocalDate.now(), LocalDate.now().plusDays(10),
                List.of(new InvoiceLine(1, "Line", Money.of("50.00", "USD"))));
        String json = InvoiceSnapshotService.toSnapshotJson(inv);
        assertTrue(json.contains("\"invoiceNumber\":\"INV-1\""));
        assertTrue(json.contains("\"status\":\"DRAFT\""));
        assertTrue(json.contains("50.00"));

        var version = InvoiceSnapshotService.snapshot(TenantId.of(UUID.randomUUID()), inv, "CREATE");
        assertEquals(1L, version.getVersion());
        assertEquals("CREATE", version.getChangeReason());
    }
}