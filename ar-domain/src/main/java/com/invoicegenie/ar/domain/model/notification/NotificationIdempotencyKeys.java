package com.invoicegenie.ar.domain.model.notification;

import com.invoicegenie.ar.domain.model.invoice.InvoiceId;

import java.time.LocalDate;
import java.util.Objects;

/**
 * Builds stable idempotency keys for notification de-duplication.
 *
 * <pre>
 * notify:{eventType}:{invoiceId}:{channel}[:qualifier]
 * - INVOICE_ISSUED: no qualifier
 * - PAYMENT_REMINDER: due:yyyy-MM-dd
 * - DUNNING_NOTICE: L{level}
 * </pre>
 */
public final class NotificationIdempotencyKeys {

    private NotificationIdempotencyKeys() {}

    public static String forInvoiceIssued(InvoiceId invoiceId, NotificationChannel channel) {
        return base(NotificationEventType.INVOICE_ISSUED, invoiceId, channel);
    }

    public static String forPaymentReminder(InvoiceId invoiceId, NotificationChannel channel, LocalDate dueDate) {
        Objects.requireNonNull(dueDate, "dueDate");
        return base(NotificationEventType.PAYMENT_REMINDER, invoiceId, channel) + ":due:" + dueDate;
    }

    public static String forDunningNotice(InvoiceId invoiceId, NotificationChannel channel, int level) {
        return base(NotificationEventType.DUNNING_NOTICE, invoiceId, channel) + ":L" + level;
    }

    public static String build(NotificationEventType eventType, InvoiceId invoiceId,
                               NotificationChannel channel, String qualifier) {
        String key = base(eventType, invoiceId, channel);
        if (qualifier != null && !qualifier.isBlank()) {
            key = key + ":" + qualifier.trim();
        }
        return key;
    }

    private static String base(NotificationEventType eventType, InvoiceId invoiceId, NotificationChannel channel) {
        Objects.requireNonNull(eventType);
        Objects.requireNonNull(invoiceId);
        Objects.requireNonNull(channel);
        return "notify:" + eventType.name() + ":" + invoiceId.getValue() + ":" + channel.name();
    }
}
