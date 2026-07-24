package com.invoicegenie.ar.application.service;

import com.invoicegenie.ar.application.port.inbound.DunningUseCase;
import com.invoicegenie.ar.application.port.outbound.EventPublisher;
import com.invoicegenie.ar.domain.event.DunningNotice;
import com.invoicegenie.ar.domain.model.customer.CustomerId;
import com.invoicegenie.ar.domain.model.invoice.Invoice;
import com.invoicegenie.ar.domain.model.invoice.InvoiceRepository;
import com.invoicegenie.shared.domain.TenantId;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;

/**
 * Application service: dunning scan emitting DunningNotice outbox events (STORY-015).
 */
public class DunningApplicationService implements DunningUseCase {

    private final InvoiceRepository invoiceRepository;
    private final EventPublisher eventPublisher;
    private final DunningPolicy policy;

    public DunningApplicationService(InvoiceRepository invoiceRepository,
                                     EventPublisher eventPublisher,
                                     DunningPolicy policy) {
        this.invoiceRepository = invoiceRepository;
        this.eventPublisher = eventPublisher;
        this.policy = policy != null ? policy : DunningPolicy.defaults();
    }

    @Override
    public DunningRunResult runForTenant(TenantId tenantId, LocalDate asOf) {
        if (!policy.isEnabled()) {
            return new DunningRunResult(0, 0, List.of());
        }
        LocalDate asOfDate = asOf != null ? asOf : LocalDate.now();
        List<Invoice> open = invoiceRepository.findOpenByTenant(tenantId);
        List<String> emitted = new ArrayList<>();
        int notices = 0;

        for (Invoice inv : open) {
            if (inv.getDueDate() == null || !inv.getDueDate().isBefore(asOfDate)) {
                continue;
            }
            if (inv.getBalanceDue().getAmount().signum() <= 0) {
                continue;
            }
            int daysPastDue = (int) ChronoUnit.DAYS.between(inv.getDueDate(), asOfDate);
            int level = policy.levelFor(daysPastDue);
            if (level <= 0) {
                continue;
            }
            CustomerId customerId = inv.getCustomerId();
            if (customerId == null) {
                continue;
            }
            eventPublisher.publish(new DunningNotice(
                    tenantId,
                    customerId,
                    inv.getId(),
                    level,
                    daysPastDue,
                    inv.getBalanceDue().getAmount().toPlainString(),
                    inv.getCurrencyCode()
            ));
            notices++;
            emitted.add(inv.getId().getValue().toString());
        }

        return new DunningRunResult(open.size(), notices, emitted);
    }
}