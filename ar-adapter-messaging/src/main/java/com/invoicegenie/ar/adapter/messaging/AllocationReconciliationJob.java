package com.invoicegenie.ar.adapter.messaging;

import com.invoicegenie.ar.application.service.AllocationIntegrityService;
import com.invoicegenie.ar.domain.model.invoice.InvoiceRepository;
import com.invoicegenie.ar.domain.model.payment.PaymentRepository;
import com.invoicegenie.ar.domain.model.tenant.Tenant;
import com.invoicegenie.ar.domain.model.tenant.TenantRepository;
import com.invoicegenie.ar.domain.model.tenant.TenantStatus;
import com.invoicegenie.shared.domain.TenantId;
import com.invoicegenie.shared.tenant.TenantContext;

import io.quarkus.scheduler.Scheduled;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Instance;
import jakarta.inject.Inject;

import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.logging.Logger;

import java.util.List;

/**
 * Periodic reconciliation: invoice amountPaid vs sum(allocations) (STORY-019).
 */
@ApplicationScoped
public class AllocationReconciliationJob {

    private static final Logger LOG = Logger.getLogger(AllocationReconciliationJob.class);

    @Inject
    Instance<InvoiceRepository> invoiceRepository;

    @Inject
    Instance<PaymentRepository> paymentRepository;

    @Inject
    Instance<TenantRepository> tenantRepository;

    @ConfigProperty(name = "invoicegenie.allocation-reconcile.enabled", defaultValue = "true")
    boolean enabled;

    @Scheduled(every = "${invoicegenie.allocation-reconcile.interval:6h}", delayed = "5m")
    public void run() {
        if (!enabled) {
            return;
        }
        if (!invoiceRepository.isResolvable() || !paymentRepository.isResolvable()
                || !tenantRepository.isResolvable()) {
            return;
        }
        AllocationIntegrityService integrity =
                new AllocationIntegrityService(invoiceRepository.get(), paymentRepository.get());
        try {
            for (Tenant tenant : tenantRepository.get().findByStatus(TenantStatus.ACTIVE)) {
                TenantId tenantId = TenantId.of(tenant.getId());
                try {
                    TenantContext.setCurrentTenant(tenantId);
                    List<AllocationIntegrityService.Mismatch> mismatches =
                            integrity.reconcileOpenInvoices(tenantId);
                    if (!mismatches.isEmpty()) {
                        LOG.warnf("Allocation integrity mismatches tenant=%s count=%d first=%s paid=%s allocSum=%s",
                                tenant.getCode(),
                                mismatches.size(),
                                mismatches.get(0).invoiceNumber(),
                                mismatches.get(0).amountPaid(),
                                mismatches.get(0).allocationsSum());
                    }
                } finally {
                    TenantContext.clear();
                }
            }
        } catch (Exception e) {
            LOG.errorf(e, "Allocation reconciliation failed: %s", e.getMessage());
        }
    }
}