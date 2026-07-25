package com.invoicegenie.ar.adapter.messaging;

import com.invoicegenie.ar.application.service.NotificationEnqueueService;
import com.invoicegenie.ar.domain.model.invoice.InvoiceId;
import com.invoicegenie.ar.domain.model.notification.NotificationEventType;
import com.invoicegenie.ar.domain.model.outbox.OutboxEntry;
import com.invoicegenie.shared.domain.TenantId;
import com.invoicegenie.shared.tenant.TenantContext;
import jakarta.enterprise.context.ApplicationScoped;
import jakarta.enterprise.inject.Instance;
import jakarta.inject.Inject;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.logging.Logger;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Bridges outbox domain events to customer notification enqueue.
 * Handles InvoiceIssued and DunningNotice.
 */
@ApplicationScoped
public class NotificationOutboxBridge {

    private static final Logger LOG = Logger.getLogger(NotificationOutboxBridge.class);

    private static final Pattern INVOICE_ID = Pattern.compile("\"invoiceId\"\\s*:\\s*\"([^\"]+)\"");
    private static final Pattern DUNNING_LEVEL = Pattern.compile("\"dunningLevel\"\\s*:\\s*(\\d+)");
    private static final Pattern DAYS_PAST = Pattern.compile("\"daysPastDue\"\\s*:\\s*(\\d+)");
    private static final Pattern BALANCE = Pattern.compile("\"balanceDue\"\\s*:\\s*\"([^\"]+)\"");

    @Inject
    Instance<NotificationEnqueueService> enqueueService;

    @ConfigProperty(name = "invoicegenie.notifications.enabled", defaultValue = "true")
    boolean enabled;

    /**
     * Called from OutboxWorker after an outbox entry is processed.
     */
    public void onOutboxPublished(OutboxEntry entry) {
        if (!enabled || entry == null || !enqueueService.isResolvable()) {
            return;
        }
        String type = entry.getEventType();
        if (type == null) {
            return;
        }
        try {
            TenantContext.setCurrentTenant(entry.getTenantId());
            if ("InvoiceIssued".equals(type)) {
                handleInvoiceIssued(entry);
            } else if ("DunningNotice".equals(type)) {
                handleDunningNotice(entry);
            }
        } catch (Exception e) {
            LOG.warnf(e, "Notification outbox bridge failed for %s id=%s", type, entry.getId());
        } finally {
            TenantContext.clear();
        }
    }

    private void handleInvoiceIssued(OutboxEntry entry) {
        UUID invoiceUuid = extractUuid(INVOICE_ID, entry.getPayload());
        if (invoiceUuid == null && entry.getAggregateId() != null) {
            invoiceUuid = entry.getAggregateId();
        }
        if (invoiceUuid == null) {
            LOG.debugf("InvoiceIssued without invoiceId, skip notification");
            return;
        }
        InvoiceId invoiceId = InvoiceId.of(invoiceUuid);
        TenantId tenantId = entry.getTenantId();
        enqueueService.get().enqueueForInvoice(
                tenantId, invoiceId, NotificationEventType.INVOICE_ISSUED,
                null, null, null, false, true);
        LOG.debugf("Enqueued INVOICE_ISSUED notifications for invoice %s", invoiceId);
    }

    private void handleDunningNotice(OutboxEntry entry) {
        UUID invoiceUuid = extractUuid(INVOICE_ID, entry.getPayload());
        if (invoiceUuid == null && entry.getAggregateId() != null) {
            invoiceUuid = entry.getAggregateId();
        }
        if (invoiceUuid == null) {
            return;
        }
        Integer level = extractInt(DUNNING_LEVEL, entry.getPayload());
        Integer days = extractInt(DAYS_PAST, entry.getPayload());
        String balance = extractString(BALANCE, entry.getPayload());

        Map<String, String> extra = new HashMap<>();
        if (level != null) {
            extra.put("dunningLevel", String.valueOf(level));
        }
        if (days != null) {
            extra.put("daysPastDue", String.valueOf(days));
        }
        if (balance != null) {
            extra.put("balanceDue", balance);
        }
        String qualifier = NotificationEnqueueService.qualifierFor(
                NotificationEventType.DUNNING_NOTICE, null, level != null ? level : 1);

        enqueueService.get().enqueueForInvoice(
                entry.getTenantId(), InvoiceId.of(invoiceUuid),
                NotificationEventType.DUNNING_NOTICE, null, qualifier, extra, false, true);
        LOG.debugf("Enqueued DUNNING_NOTICE L%s for invoice %s", level, invoiceUuid);
    }

    private static UUID extractUuid(Pattern p, String payload) {
        String s = extractString(p, payload);
        if (s == null) return null;
        try {
            return UUID.fromString(s);
        } catch (Exception e) {
            return null;
        }
    }

    private static Integer extractInt(Pattern p, String payload) {
        String s = extractString(p, payload);
        if (s == null) return null;
        try {
            return Integer.parseInt(s);
        } catch (Exception e) {
            return null;
        }
    }

    private static String extractString(Pattern p, String payload) {
        if (payload == null) return null;
        Matcher m = p.matcher(payload);
        return m.find() ? m.group(1) : null;
    }
}
