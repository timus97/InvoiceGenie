package com.invoicegenie.ar.application.service;

import com.invoicegenie.ar.domain.model.notification.NotificationChannel;

import java.util.regex.Pattern;

/**
 * Validates notification destinations (email / E.164 phone) and rejects CRLF injection.
 */
public final class NotificationDestinationValidator {

    private static final Pattern EMAIL = Pattern.compile(
            "^[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$");
    /** E.164: + and 8–15 digits */
    private static final Pattern E164 = Pattern.compile("^\\+[1-9]\\d{7,14}$");

    private NotificationDestinationValidator() {}

    public static void validateOrThrow(NotificationChannel channel, String raw) {
        if (raw == null || raw.isBlank()) {
            throw new IllegalArgumentException("destination is required");
        }
        String value = raw.trim();
        if (value.indexOf('\r') >= 0 || value.indexOf('\n') >= 0 || value.chars().anyMatch(c -> c < 32)) {
            throw new IllegalArgumentException("destination must not contain control characters");
        }
        if (value.length() > 320) {
            throw new IllegalArgumentException("destination too long");
        }
        switch (channel) {
            case EMAIL -> {
                if (!EMAIL.matcher(value).matches()) {
                    throw new IllegalArgumentException("invalid email destination");
                }
            }
            case WHATSAPP -> {
                String normalized = value.startsWith("+") ? value : "+" + value.replaceAll("\\D", "");
                if (!E164.matcher(normalized).matches() && !E164.matcher(value).matches()) {
                    // allow digits-only international without + if 8-15 digits
                    String digits = value.replaceAll("\\D", "");
                    if (digits.length() < 8 || digits.length() > 15) {
                        throw new IllegalArgumentException("invalid WhatsApp phone (use E.164, e.g. +15551234567)");
                    }
                }
            }
        }
    }

    public static String mask(String destination) {
        if (destination == null || destination.isBlank()) {
            return "(none)";
        }
        String d = destination.trim();
        if (d.contains("@")) {
            int at = d.indexOf('@');
            String local = d.substring(0, at);
            String domain = d.substring(at);
            String maskedLocal = local.isEmpty() ? "*" : local.charAt(0) + "***";
            return maskedLocal + domain;
        }
        if (d.length() <= 4) {
            return "****";
        }
        return "***" + d.substring(d.length() - 4);
    }
}