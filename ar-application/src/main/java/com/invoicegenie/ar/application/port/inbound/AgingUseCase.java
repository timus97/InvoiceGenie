package com.invoicegenie.ar.application.port.inbound;

import com.invoicegenie.ar.domain.service.AgingService;
import com.invoicegenie.shared.domain.Money;
import com.invoicegenie.shared.domain.TenantId;

import java.time.LocalDate;
import java.util.UUID;

/**
 * Inbound port: aging reports and early payment discount calculation.
 */
public interface AgingUseCase {

    /**
     * Generates an aging report for all open invoices of the tenant as of {@code asOfDate}.
     */
    AgingService.AgingReportResult getReport(TenantId tenantId, LocalDate asOfDate);

    AgingService.EarlyPaymentDiscountResult calculateEarlyPaymentDiscount(
            UUID invoiceId, Money amountDue, LocalDate dueDate, LocalDate today);
}
