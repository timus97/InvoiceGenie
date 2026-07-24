package com.invoicegenie.ar.application.service;

import com.invoicegenie.ar.domain.model.invoice.Invoice;
import com.invoicegenie.ar.domain.model.invoice.InvoiceId;
import com.invoicegenie.ar.domain.model.invoice.InvoiceRepository;
import com.invoicegenie.ar.domain.model.payment.PaymentAllocation;
import com.invoicegenie.ar.domain.model.payment.PaymentRepository;
import com.invoicegenie.shared.domain.Money;
import com.invoicegenie.shared.domain.TenantId;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

/**
 * Reconciles invoice amountPaid against sum of payment allocations (STORY-019).
 */
public class AllocationIntegrityService {

    private final InvoiceRepository invoiceRepository;
    private final PaymentRepository paymentRepository;

    public AllocationIntegrityService(InvoiceRepository invoiceRepository,
                                      PaymentRepository paymentRepository) {
        this.invoiceRepository = invoiceRepository;
        this.paymentRepository = paymentRepository;
    }

    public List<Mismatch> reconcileOpenInvoices(TenantId tenantId) {
        List<Invoice> open = invoiceRepository.findOpenByTenant(tenantId);
        List<Mismatch> mismatches = new ArrayList<>();
        for (Invoice inv : open) {
            List<PaymentAllocation> allocs =
                    paymentRepository.findAllocationsByTenantAndInvoice(tenantId, inv.getId());
            BigDecimal sum = BigDecimal.ZERO;
            String ccy = inv.getCurrencyCode();
            for (PaymentAllocation a : allocs) {
                sum = sum.add(a.getAmount().getAmount());
                ccy = a.getAmount().getCurrencyCode();
            }
            BigDecimal paid = inv.getAmountPaid() != null
                    ? inv.getAmountPaid().getAmount() : BigDecimal.ZERO;
            if (sum.compareTo(paid) != 0) {
                mismatches.add(new Mismatch(
                        inv.getId(),
                        inv.getInvoiceNumber(),
                        paid,
                        sum,
                        ccy
                ));
            }
        }
        return mismatches;
    }

    public record Mismatch(
            InvoiceId invoiceId,
            String invoiceNumber,
            BigDecimal amountPaid,
            BigDecimal allocationsSum,
            String currency
    ) {}
}