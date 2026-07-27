package com.invoicegenie.ar.application.service;

import com.invoicegenie.ar.domain.model.notification.NotificationChannel;
import com.invoicegenie.ar.domain.model.notification.NotificationSuppression;
import com.invoicegenie.ar.domain.model.notification.NotificationSuppressionRepository;
import com.invoicegenie.shared.domain.TenantId;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;

/**
 * Records and queries destination suppressions from provider bounce/complaint webhooks (PP-003).
 */
public class NotificationSuppressionService {

    private final NotificationSuppressionRepository repository;

    public NotificationSuppressionService(NotificationSuppressionRepository repository) {
        this.repository = Objects.requireNonNull(repository);
    }

    public boolean isSuppressed(TenantId tenantId, NotificationChannel channel, String destination) {
        if (destination == null || destination.isBlank()) {
            return false;
        }
        String normalized = normalize(channel, destination);
        String hash = hashDestination(normalized);
        return repository.isSuppressed(tenantId, channel, hash);
    }

    /**
     * Idempotent upsert-style record: if already suppressed, returns existing.
     */
    public NotificationSuppression suppress(TenantId tenantId, NotificationChannel channel,
                                            String destination, String reason, String provider) {
        Objects.requireNonNull(tenantId, "tenantId");
        Objects.requireNonNull(channel, "channel");
        if (destination == null || destination.isBlank()) {
            throw new IllegalArgumentException("destination is required");
        }
        String normalized = normalize(channel, destination);
        String hash = hashDestination(normalized);
        Optional<NotificationSuppression> existing =
                repository.findByTenantChannelAndHash(tenantId, channel, hash);
        if (existing.isPresent()) {
            return existing.get();
        }
        NotificationSuppression s = NotificationSuppression.create(
                tenantId, channel, normalized, hash,
                reason != null ? reason : "SUPPRESSED",
                provider != null ? provider : "unknown");
        repository.save(s);
        return s;
    }

    public static String normalize(NotificationChannel channel, String raw) {
        String v = raw.trim();
        if (channel == NotificationChannel.EMAIL) {
            return v.toLowerCase(Locale.ROOT);
        }
        // WhatsApp / phone: keep digits with leading +
        String digits = v.replaceAll("\\D", "");
        if (v.startsWith("+") || !digits.isEmpty()) {
            return "+" + digits;
        }
        return v.toLowerCase(Locale.ROOT);
    }

    public static String hashDestination(String normalized) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] dig = md.digest(normalized.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(dig);
        } catch (Exception e) {
            return Integer.toHexString(normalized.hashCode());
        }
    }
}
