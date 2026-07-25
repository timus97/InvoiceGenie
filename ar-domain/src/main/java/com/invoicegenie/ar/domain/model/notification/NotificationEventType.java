package com.invoicegenie.ar.domain.model.notification;

/**
 * Business events that trigger customer notifications (P0 MVP).
 */
public enum NotificationEventType {
    INVOICE_ISSUED,
    PAYMENT_REMINDER,
    DUNNING_NOTICE
}
