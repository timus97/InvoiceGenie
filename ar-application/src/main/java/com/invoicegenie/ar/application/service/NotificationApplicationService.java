package com.invoicegenie.ar.application.service;

import com.invoicegenie.ar.application.port.inbound.NotificationUseCase;
import com.invoicegenie.ar.domain.model.invoice.InvoiceId;
import com.invoicegenie.ar.domain.model.notification.Notification;
import com.invoicegenie.ar.domain.model.notification.NotificationAttempt;
import com.invoicegenie.ar.domain.model.notification.NotificationAttemptRepository;
import com.invoicegenie.ar.domain.model.notification.NotificationChannel;
import com.invoicegenie.ar.domain.model.notification.NotificationEventType;
import com.invoicegenie.ar.domain.model.notification.NotificationRepository;
import com.invoicegenie.ar.domain.model.notification.NotificationTemplate;
import com.invoicegenie.ar.domain.model.notification.NotificationTemplateRenderer;
import com.invoicegenie.ar.domain.model.notification.NotificationTemplateRepository;
import com.invoicegenie.ar.domain.model.outbox.AuditEntry;
import com.invoicegenie.ar.domain.model.outbox.AuditRepository;
import com.invoicegenie.shared.domain.TenantId;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * Application service: notification history + manual send + preview + metrics.
 */
public class NotificationApplicationService implements NotificationUseCase {

    private final NotificationRepository notificationRepository;
    private final NotificationAttemptRepository attemptRepository;
    private final NotificationEnqueueService enqueueService;
    private final NotificationTemplateRepository templateRepository;
    private final AuditRepository auditRepository;

    public NotificationApplicationService(NotificationRepository notificationRepository,
                                          NotificationAttemptRepository attemptRepository,
                                          NotificationEnqueueService enqueueService) {
        this(notificationRepository, attemptRepository, enqueueService, null, null);
    }

    public NotificationApplicationService(NotificationRepository notificationRepository,
                                          NotificationAttemptRepository attemptRepository,
                                          NotificationEnqueueService enqueueService,
                                          NotificationTemplateRepository templateRepository,
                                          AuditRepository auditRepository) {
        this.notificationRepository = notificationRepository;
        this.attemptRepository = attemptRepository;
        this.enqueueService = enqueueService;
        this.templateRepository = templateRepository;
        this.auditRepository = auditRepository;
    }

    @Override
    public List<Notification> list(TenantId tenantId, int limit) {
        return notificationRepository.findByTenant(tenantId, limit);
    }

    @Override
    public Optional<Notification> get(TenantId tenantId, UUID id) {
        return notificationRepository.findById(tenantId, id);
    }

    @Override
    public List<Notification> listByInvoice(TenantId tenantId, InvoiceId invoiceId, int limit) {
        return notificationRepository.findByInvoice(tenantId, invoiceId, limit);
    }

    @Override
    public List<NotificationAttempt> listAttempts(TenantId tenantId, UUID notificationId) {
        return attemptRepository.findByNotification(tenantId, notificationId);
    }

    @Override
    public List<Notification> sendForInvoice(TenantId tenantId, InvoiceId invoiceId,
                                             NotificationEventType eventType,
                                             List<NotificationChannel> channels,
                                             boolean force) {
        NotificationEventType type = eventType != null ? eventType : NotificationEventType.INVOICE_ISSUED;
        List<Notification> results = enqueueService.enqueueForInvoice(tenantId, invoiceId, type, channels,
                null, null, force, !force);
        if (auditRepository != null) {
            String after = results.stream()
                    .map(n -> n.getId() + ":" + n.getChannel() + ":" + n.getStatus())
                    .collect(Collectors.joining(","));
            auditRepository.save(tenantId, AuditEntry.create(
                    tenantId, "NOTIFICATION_SEND", invoiceId.getValue(),
                    type.name(), null,
                    "{\"results\":\"" + after + "\",\"force\":" + force + "}"));
        }
        return results;
    }

    @Override
    public PreviewResult preview(TenantId tenantId, NotificationEventType eventType,
                                 NotificationChannel channel, Map<String, String> variables) {
        if (templateRepository == null) {
            throw new IllegalStateException("Template repository not configured");
        }
        NotificationEventType type = eventType != null ? eventType : NotificationEventType.INVOICE_ISSUED;
        NotificationChannel ch = channel != null ? channel : NotificationChannel.EMAIL;
        Optional<NotificationTemplate> templateOpt =
                templateRepository.findActive(tenantId, type, ch, "en");
        if (templateOpt.isEmpty()) {
            throw new IllegalArgumentException("NO_TEMPLATE");
        }
        NotificationTemplate template = templateOpt.get();
        Map<String, String> vars = variables != null ? variables : Map.of();
        String subject = NotificationTemplateRenderer.render(template.getSubject(), vars);
        String body = NotificationTemplateRenderer.render(template.getBody(), vars);
        return new PreviewResult(type.name(), ch.name(), subject, body,
                template.getId() != null ? template.getId().toString() : null);
    }

    @Override
    public MetricsResult metrics(TenantId tenantId, int days) {
        int d = days <= 0 ? 7 : Math.min(days, 90);
        Instant since = Instant.now().minus(d, ChronoUnit.DAYS);
        List<NotificationRepository.NotificationMetricRow> rows =
                notificationRepository.countMetrics(tenantId, since);
        Map<String, Long> byStatus = new LinkedHashMap<>();
        Map<String, Long> byChannel = new LinkedHashMap<>();
        Map<String, Long> byEvent = new LinkedHashMap<>();
        long total = 0;
        List<MetricDetail> details = new ArrayList<>();
        for (NotificationRepository.NotificationMetricRow r : rows) {
            total += r.count();
            byStatus.merge(r.status(), r.count(), Long::sum);
            byChannel.merge(r.channel(), r.count(), Long::sum);
            byEvent.merge(r.eventType(), r.count(), Long::sum);
            details.add(new MetricDetail(r.status(), r.channel(), r.eventType(), r.count()));
        }
        return new MetricsResult(d, total,
                toBuckets(byStatus), toBuckets(byChannel), toBuckets(byEvent), details);
    }

    private static List<MetricBucket> toBuckets(Map<String, Long> map) {
        List<MetricBucket> list = new ArrayList<>();
        map.forEach((k, v) -> list.add(new MetricBucket(k, v)));
        return list;
    }
}
