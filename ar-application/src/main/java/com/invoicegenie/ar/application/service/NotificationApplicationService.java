package com.invoicegenie.ar.application.service;

import com.invoicegenie.ar.application.port.inbound.NotificationUseCase;
import com.invoicegenie.ar.domain.model.invoice.InvoiceId;
import com.invoicegenie.ar.domain.model.notification.Notification;
import com.invoicegenie.ar.domain.model.notification.NotificationAttempt;
import com.invoicegenie.ar.domain.model.notification.NotificationAttemptRepository;
import com.invoicegenie.ar.domain.model.notification.NotificationChannel;
import com.invoicegenie.ar.domain.model.notification.NotificationEventType;
import com.invoicegenie.ar.domain.model.notification.NotificationRepository;
import com.invoicegenie.shared.domain.TenantId;

import java.util.Base64;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Application service: notification history + manual send.
 */
public class NotificationApplicationService implements NotificationUseCase {

    private final NotificationRepository notificationRepository;
    private final NotificationAttemptRepository attemptRepository;
    private final NotificationEnqueueService enqueueService;

    public NotificationApplicationService(NotificationRepository notificationRepository,
                                          NotificationAttemptRepository attemptRepository,
                                          NotificationEnqueueService enqueueService) {
        this.notificationRepository = notificationRepository;
        this.attemptRepository = attemptRepository;
        this.enqueueService = enqueueService;
    }

    @Override
    public List<Notification> list(TenantId tenantId, int limit) {
        return notificationRepository.findByTenant(tenantId, limit);
    }

    @Override
    public PageResult list(TenantId tenantId, int limit, String cursor) {
        int safe = Math.min(Math.max(limit, 1), 500);
        NotificationRepository.PageCursor pageCursor = decodeCursor(cursor);
        NotificationRepository.Page page = notificationRepository.findByTenant(tenantId, safe, pageCursor);
        String next = page.nextCursor().map(this::encodeCursor).orElse(null);
        return new PageResult(page.items(), Optional.ofNullable(next));
    }

    private NotificationRepository.PageCursor decodeCursor(String cursor) {
        if (cursor == null || cursor.isBlank()) {
            return null;
        }
        try {
            String decoded = new String(Base64.getUrlDecoder().decode(cursor));
            String[] parts = decoded.split("\\|", 2);
            if (parts.length == 2) {
                return new NotificationRepository.PageCursor(
                        java.time.Instant.parse(parts[0]),
                        UUID.fromString(parts[1]));
            }
        } catch (Exception ignored) {
        }
        return null;
    }

    private String encodeCursor(NotificationRepository.PageCursor c) {
        String raw = c.createdAt().toString() + "|" + c.id().toString();
        return Base64.getUrlEncoder().withoutPadding().encodeToString(raw.getBytes());
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
        // force only skips event auto-flags; master/channel policy never bypassed (QA-NOTIFY-002)
        return enqueueService.enqueueForInvoice(tenantId, invoiceId, type, channels,
                null, null, force, !force);
    }
}