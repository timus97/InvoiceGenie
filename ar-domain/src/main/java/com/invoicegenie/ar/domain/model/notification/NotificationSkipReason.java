package com.invoicegenie.ar.domain.model.notification;

/**
 * Why a notification was not sent (SKIPPED).
 */
public enum NotificationSkipReason {
    GLOBAL_DISABLED,
    POLICY_DISABLED,
    CHANNEL_DISABLED,
    OPTED_OUT,
    NO_DESTINATION,
    NO_TEMPLATE,
    DUPLICATE,
    EVENT_DISABLED,
    /** Invoice not in a status that allows customer messaging (e.g. DRAFT). */
    INVALID_STATUS,
    /** Destination suppressed after hard bounce / complaint (PP-003). */
    SUPPRESSED
}
