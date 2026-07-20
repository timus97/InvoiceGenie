package com.invoicegenie.ar.adapter.api.security;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;

/**
 * Parses configured API keys: {@code key1:tenant-uuid,key2:tenant-uuid}.
 * Also validates minimal HS256 JWTs with a {@code tenant_id} claim.
 */
public final class ApiKeyRegistry {

    private final Map<String, String> keyToTenant;

    public ApiKeyRegistry(String rawConfig) {
        this.keyToTenant = parse(rawConfig);
    }

    public Optional<String> resolveTenant(String apiKey) {
        if (apiKey == null || apiKey.isBlank()) {
            return Optional.empty();
        }
        return Optional.ofNullable(keyToTenant.get(apiKey.trim()));
    }

    public boolean isEmpty() {
        return keyToTenant.isEmpty();
    }

    public int size() {
        return keyToTenant.size();
    }

    static Map<String, String> parse(String raw) {
        Map<String, String> map = new LinkedHashMap<>();
        if (raw == null || raw.isBlank()) {
            return map;
        }
        for (String part : raw.split("[,;\\n]")) {
            String entry = part.trim();
            if (entry.isEmpty()) {
                continue;
            }
            int sep = entry.indexOf(':');
            if (sep <= 0 || sep == entry.length() - 1) {
                sep = entry.indexOf('=');
            }
            if (sep <= 0 || sep == entry.length() - 1) {
                continue;
            }
            String key = entry.substring(0, sep).trim();
            String tenant = entry.substring(sep + 1).trim();
            if (!key.isEmpty() && !tenant.isEmpty()) {
                map.put(key, tenant);
            }
        }
        return map;
    }

    public static Optional<JwtClaims> validateHs256Jwt(String token, String secret) {
        if (token == null || secret == null || secret.isBlank()) {
            return Optional.empty();
        }
        String raw = token.trim();
        if (raw.toLowerCase(Locale.ROOT).startsWith("bearer ")) {
            raw = raw.substring(7).trim();
        }
        String[] parts = raw.split("\\.");
        if (parts.length != 3) {
            return Optional.empty();
        }
        try {
            byte[] signingInput = (parts[0] + "." + parts[1]).getBytes(StandardCharsets.UTF_8);
            javax.crypto.Mac mac = javax.crypto.Mac.getInstance("HmacSHA256");
            mac.init(new javax.crypto.spec.SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            byte[] expected = mac.doFinal(signingInput);
            byte[] actual = Base64.getUrlDecoder().decode(padBase64(parts[2]));
            if (!java.security.MessageDigest.isEqual(expected, actual)) {
                return Optional.empty();
            }
            String payloadJson = new String(Base64.getUrlDecoder().decode(padBase64(parts[1])), StandardCharsets.UTF_8);
            String tenantId = extractJsonString(payloadJson, "tenant_id");
            if (tenantId == null) {
                tenantId = extractJsonString(payloadJson, "tenantId");
            }
            if (tenantId == null || tenantId.isBlank()) {
                return Optional.empty();
            }
            Long exp = extractJsonLong(payloadJson, "exp");
            if (exp != null && exp < (System.currentTimeMillis() / 1000L)) {
                return Optional.empty();
            }
            String sub = extractJsonString(payloadJson, "sub");
            return Optional.of(new JwtClaims(tenantId, sub != null ? sub : "jwt-subject"));
        } catch (Exception e) {
            return Optional.empty();
        }
    }

    private static String padBase64(String s) {
        int mod = s.length() % 4;
        if (mod == 0) {
            return s;
        }
        return s + "====".substring(mod);
    }

    private static String extractJsonString(String json, String key) {
        String pattern = "\"" + key + "\"";
        int idx = json.indexOf(pattern);
        if (idx < 0) {
            return null;
        }
        int colon = json.indexOf(':', idx + pattern.length());
        if (colon < 0) {
            return null;
        }
        int startQuote = json.indexOf('"', colon + 1);
        if (startQuote < 0) {
            return null;
        }
        int endQuote = json.indexOf('"', startQuote + 1);
        if (endQuote < 0) {
            return null;
        }
        return json.substring(startQuote + 1, endQuote);
    }

    private static Long extractJsonLong(String json, String key) {
        String pattern = "\"" + key + "\"";
        int idx = json.indexOf(pattern);
        if (idx < 0) {
            return null;
        }
        int colon = json.indexOf(':', idx + pattern.length());
        if (colon < 0) {
            return null;
        }
        int i = colon + 1;
        while (i < json.length() && Character.isWhitespace(json.charAt(i))) {
            i++;
        }
        int j = i;
        while (j < json.length() && (Character.isDigit(json.charAt(j)) || json.charAt(j) == '-')) {
            j++;
        }
        if (j == i) {
            return null;
        }
        try {
            return Long.parseLong(json.substring(i, j));
        } catch (NumberFormatException e) {
            return null;
        }
    }

    public record JwtClaims(String tenantId, String subject) {}
}
