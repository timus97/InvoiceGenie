package com.invoicegenie.ar.domain.service;

import com.invoicegenie.ar.domain.model.invoice.Invoice;
import com.invoicegenie.ar.domain.model.invoice.InvoiceVersion;
import com.invoicegenie.shared.domain.TenantId;

/**
 * Builds immutable JSON snapshots for invoice version history.
 */
public final class InvoiceSnapshotService {

    private InvoiceSnapshotService() {}

    public static String toSnapshotJson(Invoice invoice) {
        StringBuilder sb = new StringBuilder(256);
        sb.append('{');
        sb.append("\"id\":\"").append(invoice.getId().getValue()).append("\",");
        sb.append("\"invoiceNumber\":\"").append(escape(invoice.getInvoiceNumber())).append("\",");
        sb.append("\"customerId\":\"").append(invoice.getCustomerId() != null ? invoice.getCustomerId().getValue() : "").append("\",");
        sb.append("\"customerRef\":\"").append(escape(invoice.getCustomerRef())).append("\",");
        sb.append("\"currencyCode\":\"").append(invoice.getCurrencyCode()).append("\",");
        sb.append("\"status\":\"").append(invoice.getStatus()).append("\",");
        sb.append("\"issueDate\":\"").append(invoice.getIssueDate()).append("\",");
        sb.append("\"dueDate\":\"").append(invoice.getDueDate()).append("\",");
        sb.append("\"total\":").append(invoice.getTotal().getAmount().toPlainString()).append(',');
        sb.append("\"amountPaid\":").append(invoice.getAmountPaid() != null
                ? invoice.getAmountPaid().getAmount().toPlainString() : "0").append(',');
        sb.append("\"version\":").append(invoice.getVersion()).append(',');
        sb.append("\"lines\":[");
        for (int i = 0; i < invoice.getLines().size(); i++) {
            var line = invoice.getLines().get(i);
            if (i > 0) sb.append(',');
            sb.append("{\"sequence\":").append(line.getSequence())
                    .append(",\"description\":\"").append(escape(line.getDescription()))
                    .append("\",\"amount\":").append(line.getLineTotal().getAmount().toPlainString())
                    .append('}');
        }
        sb.append("]}");
        return sb.toString();
    }

    public static InvoiceVersion snapshot(TenantId tenantId, Invoice invoice, String reason) {
        return InvoiceVersion.of(tenantId, invoice.getId(), invoice.getVersion(),
                toSnapshotJson(invoice), reason);
    }

    private static String escape(String s) {
        if (s == null) return "";
        return s.replace("\\", "\\\\").replace("\"", "\\\"");
    }
}

