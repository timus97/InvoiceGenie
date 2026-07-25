package com.invoicegenie.ar.domain.model.notification;

/**
 * Lifecycle status of a notification.
 */
public enum NotificationStatus {
    PENDING,
    QUEUED,
    SENDING,
    SENT,
    FAILED,
    CANCELLED,
    SKIPPED;

    /** Terminal success — must de-dupe forever for automated keys. */
    public boolean isTerminalSuccess() {
        return this == SENT;
    }

    /** Recoverable skip can be re-enqueued after contact/policy fixes. */
    public boolean isRecoverableSkip() {
        return this == SKIPPED;
    }
}
