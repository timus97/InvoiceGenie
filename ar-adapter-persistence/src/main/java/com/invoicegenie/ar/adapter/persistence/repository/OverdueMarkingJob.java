package com.invoicegenie.ar.adapter.persistence.repository;

import com.invoicegenie.ar.domain.model.invoice.Invoice;
import com.invoicegenie.ar.domain.model.invoice.InvoiceRepository;
import com.invoicegenie.ar.domain.model.invoice.InvoiceStatus;
import com.invoicegenie.ar.domain.model.outbox.AuditEntry;
import com.invoicegenie.ar.domain.model.outbox.AuditRepository;
import com.invoicegenie.ar.domain.model.tenant.Tenant;
import com.invoicegenie.ar.domain.model.tenant.TenantRepository;
import com.invoicegenie.shared.domain.TenantId;

import io.quarkus.scheduler.Scheduled;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.inject.Inject;
import jakarta.transaction.Transactional;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.logging.Logger;

import java.time.LocalDate;
import java.util.List;

/**
 * Nightly job: mark open invoices past due as OVERDUE with audit trail.
 */
@ApplicationScoped
public class OverdueMarkingJob {

    private static final Logger LOG = Logger.getLogger(OverdueMarkingJob.class);

    @Inject
    InvoiceRepository invoiceRepository;

    @Inject
    AuditRepository auditRepository;

    @Inject
    TenantRepository tenantRepository;

    @ConfigProperty(name = "overdue.marking.enabled", defaultValue = "true")
    boolean enabled;

    @Scheduled(cron = "${overdue.marking.cron:0 15 1 * * ?}", identity = "overdue-marking")
    @Transactional
    public void markAllTenants() {
        if (!enabled) {
            return;
        }
        LocalDate today = LocalDate.now();
        int total = 0;
        try {
            List<Tenant> tenants = tenantRepository.findAll();
            for (Tenant tenant : tenants) {
                total += markForTenant(TenantId.of(tenant.getId()), today);
            }
        } catch (Exception e) {
            // Tenant registry may be empty in early envs — fail soft
            LOG.debugf(e, "OverdueMarkingJob: tenant fan-out skipped or failed");
        }
        if (total > 0) {
            LOG.infof("OverdueMarkingJob marked %d invoices across tenants as of %s", total, today);
        }
    }

    /**
     * Marks eligible invoices for one tenant (callable from ops or scheduler).
     */
    @Transactional
    public int markForTenant(TenantId tenantId, LocalDate today) {
        LocalDate asOf = today != null ? today : LocalDate.now();
        List<Invoice> open = invoiceRepository.findOpenByTenant(tenantId);
        int marked = 0;
        for (Invoice inv : open) {
            if (inv.getStatus() == InvoiceStatus.OVERDUE) {
                continue;
            }
            if (!inv.isOverdue(asOf)) {
                continue;
            }
            try {
                String before = String.format("{\"status\":\"%s\"}", inv.getStatus());
                inv.markOverdue(asOf);
                invoiceRepository.save(tenantId, inv);
                String after = String.format("{\"status\":\"%s\"}", inv.getStatus());
                auditRepository.save(tenantId, AuditEntry.transition(
                        tenantId, "INVOICE", inv.getId().getValue(), inv.getInvoiceNumber(),
                        null, "MARK_OVERDUE_JOB", before, after));
                marked++;
            } catch (Exception e) {
                LOG.warnf(e, "Failed to mark invoice %s overdue", inv.getId().getValue());
            }
        }
        return marked;
    }
}
