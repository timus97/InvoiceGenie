package com.invoicegenie.ar.application.service;

import com.invoicegenie.ar.application.port.inbound.StatementUseCase;
import com.invoicegenie.ar.domain.model.customer.Customer;
import com.invoicegenie.ar.domain.model.customer.CustomerId;
import com.invoicegenie.ar.domain.model.invoice.Invoice;
import com.invoicegenie.ar.domain.model.invoice.InvoiceId;
import com.invoicegenie.ar.domain.model.invoice.InvoiceLine;
import com.invoicegenie.shared.domain.Money;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("PdfDocumentService (PP-012)")
class PdfDocumentServiceTest {

    private final PdfDocumentService service = new PdfDocumentService();

    @Test
    @DisplayName("generates non-empty invoice PDF")
    void invoicePdf() {
        InvoiceId id = InvoiceId.of(UUID.randomUUID());
        CustomerId cid = CustomerId.of(UUID.randomUUID());
        Invoice inv = new Invoice(id, "INV-100", cid, "Acme", "USD",
                LocalDate.now(), LocalDate.now().plusDays(30),
                List.of(new InvoiceLine(1, "Consulting", Money.of(new BigDecimal("250.00"), "USD"))));
        inv.issue();
        Customer customer = new Customer(cid, "ACME", "Acme Corp", "USD");

        byte[] pdf = service.generateInvoicePdf(inv, customer);
        assertNotNull(pdf);
        assertTrue(pdf.length > 100);
        // PDF magic header
        assertEquals('%', (char) pdf[0]);
        assertEquals('P', (char) pdf[1]);
        assertEquals('D', (char) pdf[2]);
        assertEquals('F', (char) pdf[3]);
    }

    @Test
    @DisplayName("generates non-empty statement PDF")
    void statementPdf() {
        StatementUseCase.CustomerStatement stmt = new StatementUseCase.CustomerStatement(
                UUID.randomUUID().toString(),
                "ACME",
                "Acme Corp",
                LocalDate.now(),
                List.of(new StatementUseCase.OpenItem(
                        UUID.randomUUID().toString(), "INV-1",
                        LocalDate.now().minusDays(10), LocalDate.now().minusDays(1),
                        1, new BigDecimal("100"), BigDecimal.ZERO, new BigDecimal("100"),
                        "USD", "OVERDUE")),
                new BigDecimal("100"),
                "USD"
        );
        byte[] pdf = service.generateStatementPdf(stmt);
        assertNotNull(pdf);
        assertTrue(pdf.length > 100);
        assertEquals('%', (char) pdf[0]);
        assertEquals('P', (char) pdf[1]);
    }
}
