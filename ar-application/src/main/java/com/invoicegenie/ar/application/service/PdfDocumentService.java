package com.invoicegenie.ar.application.service;

import com.invoicegenie.ar.application.port.inbound.StatementUseCase;
import com.invoicegenie.ar.domain.model.customer.Customer;
import com.invoicegenie.ar.domain.model.invoice.Invoice;
import com.invoicegenie.ar.domain.model.invoice.InvoiceLine;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDType1Font;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.util.List;

/**
 * Generates simple professional invoice and statement PDFs via Apache PDFBox (PP-012).
 */
public class PdfDocumentService {

    private static final float MARGIN = 50f;
    private static final float LINE_HEIGHT = 14f;

    public byte[] generateInvoicePdf(Invoice invoice, Customer customer) {
        try (PDDocument doc = new PDDocument(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            PDPage page = new PDPage(PDRectangle.LETTER);
            doc.addPage(page);
            try (PDPageContentStream cs = new PDPageContentStream(doc, page)) {
                float y = page.getMediaBox().getHeight() - MARGIN;
                y = writeTitle(cs, y, "INVOICE");
                y = writeLine(cs, y, "Invoice #: " + nullSafe(invoice.getInvoiceNumber()));
                y = writeLine(cs, y, "Issue date: " + nullSafe(invoice.getIssueDate()));
                y = writeLine(cs, y, "Due date: " + nullSafe(invoice.getDueDate()));
                y = writeLine(cs, y, "Status: " + (invoice.getStatus() != null ? invoice.getStatus().name() : ""));
                y -= LINE_HEIGHT;
                String billTo = customer != null ? customer.getDisplayName() : invoice.getCustomerRef();
                y = writeLine(cs, y, "Bill to: " + nullSafe(billTo));
                if (customer != null && customer.getEmail() != null) {
                    y = writeLine(cs, y, "Email: " + customer.getEmail());
                }
                y -= LINE_HEIGHT;
                y = writeLine(cs, y, "Line items:");
                y = writeLine(cs, y, String.format("%-4s %-36s %8s %12s %12s",
                        "#", "Description", "Qty", "Unit", "Total"));
                List<InvoiceLine> lines = invoice.getLines();
                int i = 1;
                for (InvoiceLine line : lines) {
                    if (y < MARGIN + 80) {
                        break; // keep single page for pilot
                    }
                    String desc = truncate(line.getDescription(), 36);
                    y = writeLine(cs, y, String.format("%-4d %-36s %8s %12s %12s",
                            i++,
                            desc,
                            line.getQuantity().toPlainString(),
                            money(line.getUnitPrice() != null ? line.getUnitPrice().getAmount() : null),
                            money(line.getLineTotal() != null ? line.getLineTotal().getAmount() : null)));
                }
                y -= LINE_HEIGHT;
                String ccy = invoice.getCurrencyCode() != null ? invoice.getCurrencyCode() : "";
                y = writeLine(cs, y, "Subtotal: " + money(invoice.getSubtotal() != null ? invoice.getSubtotal().getAmount() : null) + " " + ccy);
                y = writeLine(cs, y, "Tax: " + money(invoice.getTaxTotal() != null ? invoice.getTaxTotal().getAmount() : null) + " " + ccy);
                y = writeLine(cs, y, "Total: " + money(invoice.getTotal() != null ? invoice.getTotal().getAmount() : null) + " " + ccy);
                y = writeLine(cs, y, "Amount paid: " + money(invoice.getAmountPaid() != null ? invoice.getAmountPaid().getAmount() : BigDecimal.ZERO) + " " + ccy);
                y = writeLine(cs, y, "Balance due: " + money(invoice.getBalanceDue() != null ? invoice.getBalanceDue().getAmount() : null) + " " + ccy);
                if (invoice.getNotes() != null && !invoice.getNotes().isBlank()) {
                    y -= LINE_HEIGHT;
                    writeLine(cs, y, "Notes: " + truncate(invoice.getNotes(), 80));
                }
            }
            doc.save(out);
            return out.toByteArray();
        } catch (IOException e) {
            throw new IllegalStateException("Failed to generate invoice PDF", e);
        }
    }

    public byte[] generateStatementPdf(StatementUseCase.CustomerStatement statement) {
        try (PDDocument doc = new PDDocument(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            PDPage page = new PDPage(PDRectangle.LETTER);
            doc.addPage(page);
            try (PDPageContentStream cs = new PDPageContentStream(doc, page)) {
                float y = page.getMediaBox().getHeight() - MARGIN;
                y = writeTitle(cs, y, "ACCOUNT STATEMENT");
                y = writeLine(cs, y, "Customer: " + nullSafe(statement.customerName())
                        + " (" + nullSafe(statement.customerCode()) + ")");
                y = writeLine(cs, y, "As of: " + nullSafe(statement.asOfDate()));
                y = writeLine(cs, y, "Currency: " + nullSafe(statement.currency()));
                y -= LINE_HEIGHT;
                y = writeLine(cs, y, String.format("%-16s %-12s %-12s %12s %12s",
                        "Invoice", "Issue", "Due", "Balance", "Status"));
                for (StatementUseCase.OpenItem item : statement.openItems()) {
                    if (y < MARGIN + 60) {
                        break;
                    }
                    y = writeLine(cs, y, String.format("%-16s %-12s %-12s %12s %12s",
                            truncate(item.invoiceNumber(), 16),
                            nullSafe(item.issueDate()),
                            nullSafe(item.dueDate()),
                            money(item.balanceDue()),
                            truncate(item.status(), 12)));
                }
                y -= LINE_HEIGHT;
                writeLine(cs, y, "Total open balance: "
                        + money(statement.totalBalance()) + " " + nullSafe(statement.currency()));
            }
            doc.save(out);
            return out.toByteArray();
        } catch (IOException e) {
            throw new IllegalStateException("Failed to generate statement PDF", e);
        }
    }

    private static float writeTitle(PDPageContentStream cs, float y, String text) throws IOException {
        cs.beginText();
        cs.setFont(PDType1Font.HELVETICA_BOLD, 16);
        cs.newLineAtOffset(MARGIN, y);
        cs.showText(sanitize(text));
        cs.endText();
        return y - 22f;
    }

    private static float writeLine(PDPageContentStream cs, float y, String text) throws IOException {
        cs.beginText();
        cs.setFont(PDType1Font.HELVETICA, 10);
        cs.newLineAtOffset(MARGIN, y);
        cs.showText(sanitize(text));
        cs.endText();
        return y - LINE_HEIGHT;
    }

    private static String sanitize(String s) {
        if (s == null) return "";
        // PDF Type1 Helvetica is WinAnsi — strip non-latin1
        StringBuilder sb = new StringBuilder(s.length());
        for (char c : s.toCharArray()) {
            if (c == '\n' || c == '\r' || c == '\t') {
                sb.append(' ');
            } else if (c >= 32 && c < 127) {
                sb.append(c);
            } else if (c <= 255) {
                sb.append(c);
            } else {
                sb.append('?');
            }
        }
        return sb.toString();
    }

    private static String nullSafe(Object o) {
        return o == null ? "" : o.toString();
    }

    private static String money(BigDecimal v) {
        return v == null ? "0.00" : v.setScale(2, java.math.RoundingMode.HALF_UP).toPlainString();
    }

    private static String truncate(String s, int max) {
        if (s == null) return "";
        return s.length() <= max ? s : s.substring(0, max - 1) + "...";
    }
}
