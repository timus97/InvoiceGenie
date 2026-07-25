package com.invoicegenie.ar.application.service;

import com.invoicegenie.shared.domain.TenantId;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Simple in-process per-tenant rate limiter for manual notification send.
 * Pilot-safe; replace with distributed limiter for multi-instance prod.
 */
public class NotificationRateLimiter {

    private final int maxPerMinute;
    private final Map<String, Window> windows = new ConcurrentHashMap<>();

    public NotificationRateLimiter(int maxPerMinute) {
        this.maxPerMinute = Math.max(1, maxPerMinute);
    }

    public void checkOrThrow(TenantId tenantId) {
        String key = tenantId.getValue().toString();
        long minute = System.currentTimeMillis() / 60_000L;
        Window w = windows.compute(key, (k, prev) -> {
            if (prev == null || prev.minute != minute) {
                return new Window(minute, new AtomicInteger(0));
            }
            return prev;
        });
        int n = w.count.incrementAndGet();
        if (n > maxPerMinute) {
            throw new IllegalStateException(
                    "RATE_LIMITED: max " + maxPerMinute + " notification sends per minute per tenant");
        }
    }

    private static final class Window {
        final long minute;
        final AtomicInteger count;

        Window(long minute, AtomicInteger count) {
            this.minute = minute;
            this.count = count;
        }
    }
}