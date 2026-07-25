package com.invoicegenie.ar.application.port.inbound;

import com.invoicegenie.ar.domain.model.customer.CustomerId;
import com.invoicegenie.ar.domain.model.invoice.InvoiceId;
import com.invoicegenie.ar.domain.model.notification.Notification;
import com.invoicegenie.ar.domain.model.notification.NotificationAttempt;
import com.invoicegenie.ar.domain.model.notification.NotificationChannel;
import com.invoicegenie.ar.domain.model.notification.NotificationEventType;
import com.invoicegenie.shared.domain.TenantId;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Inbound port: customer notifications history + manual send.
 */
public interface NotificationUseCase {

    List<Notification> list(TenantId tenantId, int limit);

    Optional<Notification> get(TenantId tenantId, UUID id);

    List<Notification> listByInvoice(TenantId tenantId, InvoiceId invoiceId, int limit);

    List<NotificationAttempt> listAttempts(TenantId tenantId, UUID notificationId);

    /**
     * Manual send for an invoice (respects prefs; force may override auto-policy flags only).
     */
    List<Notification> sendForInvoice(TenantId tenantId, InvoiceId invoiceId,
                                      NotificationEventType eventType,
                                      List<NotificationChannel> channels,
                                      boolean force);

    record SendCommand(InvoiceId invoiceId, NotificationEventType eventType,
                       List<NotificationChannel> channels, boolean force) {}
}
