package com.invoicegenie.ar.application.service;

import com.invoicegenie.ar.application.port.inbound.AgingUseCase;
import com.invoicegenie.ar.domain.model.invoice.Invoice;
import com.invoicegenie.ar.domain.model.invoice.InvoiceRepository;
import com.invoicegenie.ar.domain.service.AgingService;
import com.invoicegenie.shared.domain.Money;
import com.invoicegenie.shared.domain.TenantId;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Application service: aging report and early payment discount.
 *
 * <p>Loads open invoices from {@link InvoiceRepository} and delegates report
 * generation to {@link AgingService}.
 */
public class AgingApplicationService implements AgingUseCase {

    private final AgingService agingService;
    private final InvoiceRepository invoiceRepository;

    public AgingApplicationService(AgingService agingService,
                                   InvoiceRepository invoiceRepository) {
        this.agingService = agingService;
        this.invoiceRepository = invoiceRepository;
    }

    @Override
    public AgingService.AgingReportResult getReport(TenantId tenantId, LocalDate asOfDate) {
        LocalDate effectiveDate = asOfDate != null ? asOfDate : LocalDate.now();
        List<Invoice> openInvoices = invoiceRepository.findOpenByTenant(tenantId);

        List<AgingService.InvoiceWithBalance> withBalances = openInvoices.stream()
                .map(inv -> {
                    UUID customerId;
                    try {
                        customerId = UUID.fromString(inv.getCustomerRef());
                    } catch (Exception e) {
                        customerId = new UUID(0L, 0L);
                    }
                    return new AgingService.InvoiceWithBalance(
                            inv.getId().getValue(),
                            inv.getInvoiceNumber(),
                            customerId,
                            inv.getCustomerRef(),
                            inv.getBalanceDue(),
                            inv.getDueDate(),
                            inv.getStatus()
                    );
                })
                .toList();

        return agingService.generateAgingReport(tenantId, effectiveDate, withBalances);
    }

    @Override
    public AgingService.EarlyPaymentDiscountResult calculateEarlyPaymentDiscount(
            UUID invoiceId, Money amountDue, LocalDate dueDate, LocalDate today) {
        return agingService.calculateEarlyPaymentDiscount(invoiceId, amountDue, dueDate, today);
    }
}
