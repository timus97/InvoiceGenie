package com.invoicegenie.ar.domain.model.notification;

import com.invoicegenie.shared.domain.TenantId;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/**
 * Single delivery attempt audit row.
 */
public final class NotificationAttempt {

    private final UUID id;
    private final TenantId tenantId;
    private final UUID notificationId;
    private final int attemptNumber;
    private final NotificationAttemptStatus status;
    private final String provider;
    private final String providerMessageId;
    private final Integer httpStatus;
    private final String errorMessage;
    private final String responseSnippet;
    private final Instant attemptedAt;

    public NotificationAttempt(UUID id, TenantId tenantId, UUID notificationId, int attemptNumber,
                               NotificationAttemptStatus status, String provider,
                               String providerMessageId, Integer httpStatus,
                               String errorMessage, String responseSnippet, Instant attemptedAt) {
        this.id = Objects.requireNonNull(id);
        this.tenantId = Objects.requireNonNull(tenantId);
        this.notificationId = Objects.requireNonNull(notificationId);
        this.attemptNumber = attemptNumber;
        this.status = Objects.requireNonNull(status);
        this.provider = provider;
        this.providerMessageId = providerMessageId;
        this.httpStatus = httpStatus;
        this.errorMessage = truncate(errorMessage);
        this.responseSnippet = truncate(responseSnippet);
        this.attemptedAt = attemptedAt != null ? attemptedAt : Instant.now();
    }

    public static NotificationAttempt success(TenantId tenantId, UUID notificationId, int attemptNumber,
                                              String provider, String providerMessageId, String snippet) {
        return new NotificationAttempt(UUID.randomUUID(), tenantId, notificationId, attemptNumber,
                NotificationAttemptStatus.SUCCESS, provider, providerMessageId, null, null, snippet, Instant.now());
    }

    public static NotificationAttempt failure(TenantId tenantId, UUID notificationId, int attemptNumber,
                                              String provider, Integer httpStatus, String error) {
        return new NotificationAttempt(UUID.randomUUID(), tenantId, notificationId, attemptNumber,
                NotificationAttemptStatus.FAILED, provider, null, httpStatus, error, null, Instant.now());
    }

    private static String truncate(String s) {
        if (s == null) return null;
        return s.length() <= 2000 ? s : s.substring(0, 2000);
    }

    public UUID getId() { return id; }
    public TenantId getTenantId() { return tenantId; }
    public UUID getNotificationId() { return notificationId; }
    public int getAttemptNumber() { return attemptNumber; }
    public NotificationAttemptStatus getStatus() { return status; }
    public String getProvider() { return provider; }
    public String getProviderMessageId() { return providerMessageId; }
    public Integer getHttpStatus() { return httpStatus; }
    public String getErrorMessage() { return errorMessage; }
    public String getResponseSnippet() { return responseSnippet; }
    public Instant getAttemptedAt() { return attemptedAt; }
}
