package com.invoicegenie.ar.adapter.api.security;

import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Set;

/**
 * Maps IdP group/role claim values onto InvoiceGenie AR roles (PP-020).
 */
public final class OidcRoleMapper {

    private OidcRoleMapper() {}

    /**
     * Prefer explicit roles claim, then groups. Unknown values are ignored.
     * Recognized: AR_CLERK, AR_CONTROLLER, AR_AUDITOR, TENANT_ADMIN (case-insensitive).
     * Also maps common aliases: clerk, controller, auditor, admin / tenant-admin.
     */
    @SafeVarargs
    public static Set<String> mapRoles(Set<String>... sources) {
        Set<String> out = new LinkedHashSet<>();
        if (sources == null) {
            return out;
        }
        for (Set<String> src : sources) {
            if (src == null) {
                continue;
            }
            for (String raw : src) {
                String mapped = mapOne(raw);
                if (mapped != null) {
                    out.add(mapped);
                }
            }
        }
        return out;
    }

    static String mapOne(String raw) {
        if (raw == null || raw.isBlank()) {
            return null;
        }
        String v = raw.trim();
        // strip common prefix paths like /AR_CLERK or ROLE_AR_CLERK
        int slash = v.lastIndexOf('/');
        if (slash >= 0 && slash < v.length() - 1) {
            v = v.substring(slash + 1);
        }
        if (v.regionMatches(true, 0, "ROLE_", 0, 5)) {
            v = v.substring(5);
        }
        String upper = v.toUpperCase(Locale.ROOT).replace('-', '_').replace(' ', '_');
        return switch (upper) {
            case "AR_CLERK", "CLERK" -> ArRoles.AR_CLERK;
            case "AR_CONTROLLER", "CONTROLLER" -> ArRoles.AR_CONTROLLER;
            case "AR_AUDITOR", "AUDITOR" -> ArRoles.AR_AUDITOR;
            case "TENANT_ADMIN", "ADMIN", "TENANTADMIN" -> ArRoles.TENANT_ADMIN;
            default -> {
                // exact product role already
                if (ArRoles.AR_CLERK.equals(upper) || ArRoles.AR_CONTROLLER.equals(upper)
                        || ArRoles.AR_AUDITOR.equals(upper) || ArRoles.TENANT_ADMIN.equals(upper)) {
                    yield upper;
                }
                yield null;
            }
        };
    }
}
