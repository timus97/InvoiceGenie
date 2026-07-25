package com.invoicegenie.ar.adapter.messaging;

import com.invoicegenie.ar.application.service.NotificationEnqueueService;
import com.invoicegenie.ar.domain.model.invoice.Invoice;
import com.invoicegenie.ar.domain.model.invoice.InvoiceRepository;
import com.invoicegenie.ar.domain.model.notification.NotificationEventType;
import com.invoicegenie.ar.domain.model.notification.NotificationPolicy;
import com.invoicegenie.ar.domain.model.notification.NotificationPolicyRepository;
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
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Pre-due payment reminder job.
 * Catch-up: due date between today and today+preDueDays (inclusive of target day) (QA-NOTIFY-009).
 */
@ApplicationScoped
public class PaymentReminderJob {

    private static final Logger LOG = Logger.getLogger(PaymentReminderJob.class);

    @Inject
    Instance<TenantRepository> tenantRepository;

    @Inject
    Instance<InvoiceRepository> invoiceRepository;

    @Inject
    Instance<NotificationPolicyRepository> policyRepository;

    @Inject
    Instance<NotificationEnqueueService> enqueueService;

    @ConfigProperty(name = "invoicegenie.notifications.enabled", defaultValue = "true")
    boolean enabled;

    @ConfigProperty(name = "invoicegenie.notifications.pre-due-days", defaultValue = "3")
    int defaultPreDueDays;

    @Scheduled(every = "${invoicegenie.notifications.reminder.job-interval:1h}", delayed = "3m")
    public void run() {
        if (!enabled) {
            return;
        }
        if (!tenantRepository.isResolvable() || !invoiceRepository.isResolvable()
                || !policyRepository.isResolvable() || !enqueueService.isResolvable()) {
            LOG.debug("Payment reminder job skipped: beans not available");
            return;
        }
        LocalDate today = LocalDate.now();
        try {
            for (Tenant tenant : tenantRepository.get().findByStatus(TenantStatus.ACTIVE)) {
                TenantId tenantId = TenantId.of(tenant.getId());
                try {
                    TenantContext.setCurrentTenant(tenantId);
                    processTenant(tenantId, today);
                } finally {
                    TenantContext.clear();
                }
            }
        } catch (Exception e) {
            LOG.errorf(e, "Payment reminder job failed: %s", e.getMessage());
        }
    }

    private void processTenant(TenantId tenantId, LocalDate today) {
        NotificationPolicy policy = policyRepository.get().findByTenant(tenantId)
                .orElseGet(() -> NotificationPolicy.defaults(tenantId));
        if (!policy.isEnabled() || !policy.isPreDueReminderEnabled()) {
            return;
        }
        // Honor preDueDays = 0 (same-day) — only fall back when policy row missing (defaults used above)
        int days = policy.getPreDueDays() >= 0 ? policy.getPreDueDays() : defaultPreDueDays;
        LocalDate windowStart = today;
        LocalDate windowEnd = today.plusDays(days);

        List<Invoice> open = invoiceRepository.get().findOpenByTenant(tenantId);
        int enqueued = 0;
        for (Invoice inv : open) {
            if (inv.getDueDate() == null) {
                continue;
            }
            // Catch-up: due date in [today, today+preDueDays] (QA-NOTIFY-009)
            LocalDate due = inv.getDueDate();
            if (due.isBefore(windowStart) || due.isAfter(windowEnd)) {
                continue;
            }
            if (inv.getBalanceDue() == null || inv.getBalanceDue().getAmount().signum() <= 0) {
                continue;
            }
            Map<String, String> extra = new HashMap<>();
            extra.put("balanceDue", inv.getBalanceDue().getAmount().toPlainString());
            String qualifier = NotificationEnqueueService.qualifierFor(
                    NotificationEventType.PAYMENT_REMINDER, inv.getDueDate(), null);
            var results = enqueueService.get().enqueueForInvoice(
                    tenantId, inv.getId(), NotificationEventType.PAYMENT_REMINDER,
                    null, qualifier, extra, false, true);
            enqueued += results.size();
        }
        if (enqueued > 0) {
            LOG.infof("Payment reminders tenant=%s window=%s..%s enqueued=%d",
                    tenantId, windowStart, windowEnd, enqueued);
        }
    }
}