package com.invoicegenie.ar.application.service;

import com.invoicegenie.ar.domain.model.customer.CustomerId;
import com.invoicegenie.ar.domain.model.notification.NotificationChannel;
import com.invoicegenie.shared.domain.TenantId;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Base64;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * HMAC-signed unsubscribe tokens embedding tenant, customer, channel (PP-011).
 *
 * <p>Format: {@code base64url(payload).base64url(hmacSha256)} where payload is
 * {@code tenantId|customerId|channel|expEpochSec}.
 */
public class UnsubscribeTokenService {

    private static final String HMAC_ALG = "HmacSHA256";
    private static final long DEFAULT_TTL_SECONDS = 365L * 24 * 3600; // 1 year

    private final byte[] secret;
    private final long ttlSeconds;

    public UnsubscribeTokenService(String secret) {
        this(secret, DEFAULT_TTL_SECONDS);
    }

    public UnsubscribeTokenService(String secret, long ttlSeconds) {
        if (secret == null || secret.isBlank() || "none".equalsIgnoreCase(secret.trim())) {
            // Deterministic local fallback so tokens still work in dev when secret not configured
            secret = "invoicegenie-dev-unsubscribe-secret-change-me";
        }
        this.secret = secret.getBytes(StandardCharsets.UTF_8);
        this.ttlSeconds = ttlSeconds > 0 ? ttlSeconds : DEFAULT_TTL_SECONDS;
    }

    public String generate(TenantId tenantId, CustomerId customerId, NotificationChannel channel) {
        Objects.requireNonNull(tenantId);
        Objects.requireNonNull(customerId);
        Objects.requireNonNull(channel);
        long exp = Instant.now().getEpochSecond() + ttlSeconds;
        String payload = tenantId.getValue() + "|" + customerId.getValue() + "|"
                + channel.name() + "|" + exp;
        String payloadB64 = b64(payload.getBytes(StandardCharsets.UTF_8));
        String sig = b64(hmac(payloadB64.getBytes(StandardCharsets.UTF_8)));
        return payloadB64 + "." + sig;
    }

    public Optional<TokenClaims> parse(String token) {
        if (token == null || token.isBlank()) {
            return Optional.empty();
        }
        String[] parts = token.trim().split("\\.");
        if (parts.length != 2) {
            return Optional.empty();
        }
        String payloadB64 = parts[0];
        String sig = parts[1];
        String expected = b64(hmac(payloadB64.getBytes(StandardCharsets.UTF_8)));
        if (!constantTimeEquals(sig, expected)) {
            return Optional.empty();
        }
        try {
            String payload = new String(Base64.getUrlDecoder().decode(payloadB64), StandardCharsets.UTF_8);
            String[] fields = payload.split("\\|");
            if (fields.length != 4) {
                return Optional.empty();
            }
            UUID tenantUuid = UUID.fromString(fields[0]);
            UUID customerUuid = UUID.fromString(fields[1]);
            NotificationChannel channel = NotificationChannel.valueOf(fields[2]);
            long exp = Long.parseLong(fields[3]);
            if (Instant.now().getEpochSecond() > exp) {
                return Optional.empty();
            }
            return Optional.of(new TokenClaims(
                    TenantId.of(tenantUuid),
                    CustomerId.of(customerUuid),
                    channel,
                    Instant.ofEpochSecond(exp)));
        } catch (Exception e) {
            return Optional.empty();
        }
    }

    private byte[] hmac(byte[] data) {
        try {
            Mac mac = Mac.getInstance(HMAC_ALG);
            mac.init(new SecretKeySpec(secret, HMAC_ALG));
            return mac.doFinal(data);
        } catch (Exception e) {
            throw new IllegalStateException("HMAC failure", e);
        }
    }

    private static String b64(byte[] bytes) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private static boolean constantTimeEquals(String a, String b) {
        if (a == null || b == null) return false;
        byte[] x = a.getBytes(StandardCharsets.UTF_8);
        byte[] y = b.getBytes(StandardCharsets.UTF_8);
        if (x.length != y.length) {
            // still compare to keep rough timing
            int acc = x.length ^ y.length;
            for (int i = 0; i < x.length; i++) {
                acc |= x[i] ^ x[i];
            }
            return false;
        }
        int r = 0;
        for (int i = 0; i < x.length; i++) {
            r |= x[i] ^ y[i];
        }
        return r == 0;
    }

    public record TokenClaims(TenantId tenantId, CustomerId customerId,
                              NotificationChannel channel, Instant expiresAt) {}
}
