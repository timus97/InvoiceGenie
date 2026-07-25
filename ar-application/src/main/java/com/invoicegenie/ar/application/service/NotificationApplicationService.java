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