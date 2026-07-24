package com.invoicegenie.ar.adapter.messaging;

import com.invoicegenie.ar.application.port.inbound.DunningUseCase;
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

import java.time.LocalDate;

/**
 * Scheduled dunning foundation job (STORY-015).
 * Emits DunningNotice outbox events for overdue open invoices per tenant.
 */
@ApplicationScoped
public class DunningJob {

    private static final Logger LOG = Logger.getLogger(DunningJob.class);

    @Inject
    Instance<DunningUseCase> dunningUseCase;

    @Inject
    Instance<TenantRepository> tenantRepository;

    @ConfigProperty(name = "invoicegenie.dunning.enabled", defaultValue = "true")
    boolean enabled;

    @Scheduled(every = "${invoicegenie.dunning.job-interval:1h}", delayed = "2m")
    public void run() {
        if (!enabled) {
            return;
        }
        if (!dunningUseCase.isResolvable() || !tenantRepository.isResolvable()) {
            LOG.debug("Dunning job skipped: use case or tenant repository not available");
            return;
        }
        try {
            for (Tenant tenant : tenantRepository.get().findByStatus(TenantStatus.ACTIVE)) {
                TenantId tenantId = TenantId.of(tenant.getId());
                try {
                    TenantContext.setCurrentTenant(tenantId);
                    var result = dunningUseCase.get().runForTenant(tenantId, LocalDate.now());
                    if (result.noticesEmitted() > 0) {
                        LOG.infof("Dunning tenant=%s scanned=%d notices=%d",
                                tenant.getCode(), result.invoicesScanned(), result.noticesEmitted());
                    }
                } finally {
                    TenantContext.clear();
                }
            }
        } catch (Exception e) {
            LOG.errorf(e, "Dunning job failed: %s", e.getMessage());
        }
    }
}