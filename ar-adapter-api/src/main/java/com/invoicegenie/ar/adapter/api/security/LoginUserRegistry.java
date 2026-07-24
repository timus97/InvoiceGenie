package com.invoicegenie.ar.adapter.api.security;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

/**
 * Parses configured web login users:
 * {@code user:password:tenantUuid:ROLE1|ROLE2,other:pass:tenant:AR_CLERK}.
 * Passwords are compared with constant-time equality (plaintext config for MVP;
 * full OIDC remains the long-term path).
 */
public final class LoginUserRegistry {

    private final Map<String, LoginUser> users;

    public LoginUserRegistry(String rawConfig) {
        this.users = parse(rawConfig);
    }

    public Optional<LoginUser> authenticate(String username, String password) {
        if (username == null || password == null) {
            return Optional.empty();
        }
        LoginUser user = users.get(username.trim().toLowerCase(Locale.ROOT));
        if (user == null) {
            return Optional.empty();
        }
        if (!constantTimeEquals(user.password(), password)) {
            return Optional.empty();
        }
        return Optional.of(user);
    }

    public boolean isEmpty() {
        return users.isEmpty();
    }

    public int size() {
        return users.size();
    }

    static Map<String, LoginUser> parse(String raw) {
        Map<String, LoginUser> map = new LinkedHashMap<>();
        if (raw == null || raw.isBlank()) {
            return map;
        }
        for (String part : raw.split("[,;\n]")) {
            String entry = part.trim();
            if (entry.isEmpty()) {
                continue;
            }
            List<String> segments = splitLimited(entry, ':', 4);
            if (segments.size() < 3) {
                continue;
            }
            String user = segments.get(0).trim();
            String password = segments.get(1);
            String tenant = segments.get(2).trim();
            Set<String> roles = new LinkedHashSet<>();
            if (segments.size() >= 4 && segments.get(3) != null && !segments.get(3).isBlank()) {
                for (String r : segments.get(3).split("[|+]")) {
                    String role = r.trim().toUpperCase(Locale.ROOT);
                    if (!role.isEmpty()) {
                        roles.add(role);
                    }
                }
            }
            if (roles.isEmpty()) {
                roles.add(ArRoles.AR_CLERK);
            }
            if (!user.isEmpty() && password != null && !tenant.isEmpty()) {
                map.put(user.toLowerCase(Locale.ROOT), new LoginUser(user, password, tenant, Set.copyOf(roles)));
            }
        }
        return map;
    }

    /** Split into at most {@code maxParts} segments (last segment keeps remaining colons). */
    static List<String> splitLimited(String s, char sep, int maxParts) {
        List<String> out = new ArrayList<>();
        if (s == null) {
            return out;
        }
        int start = 0;
        for (int n = 1; n < maxParts; n++) {
            int idx = s.indexOf(sep, start);
            if (idx < 0) {
                break;
            }
            out.add(s.substring(start, idx));
            start = idx + 1;
        }
        out.add(s.substring(start));
        return out;
    }

    static boolean constantTimeEquals(String a, String b) {
        if (a == null || b == null) {
            return false;
        }
        byte[] left = a.getBytes(StandardCharsets.UTF_8);
        byte[] right = b.getBytes(StandardCharsets.UTF_8);
        if (left.length != right.length) {
            MessageDigest.isEqual(left, left);
            return false;
        }
        return MessageDigest.isEqual(left, right);
    }

    public record LoginUser(String username, String password, String tenantId, Set<String> roles) {
        public LoginUser {
            roles = roles == null ? Set.of() : Set.copyOf(roles);
        }
    }
}
