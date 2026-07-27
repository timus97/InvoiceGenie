package com.invoicegenie.ar.domain.model.notification;

/**
 * Business events that trigger customer notifications.
 */
public enum NotificationEventType {
    INVOICE_ISSUED,
    PAYMENT_REMINDER,
    DUNNING_NOTICE,
    /** Manual / workflow customer account statement email (PP-013). */
    STATEMENT_SEND
}
