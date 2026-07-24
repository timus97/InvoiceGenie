package com.invoicegenie.ar.adapter.api.security;

import java.util.Set;

/**
 * Product roles for Phase-1 RBAC (STORY-003).
 * JWT claim {@code roles} is a string array of these values.
 * API-key (M2M) auth is granted {@link #M2M_ROLES}.
 */
public final class ArRoles {
    public static final String AR_CLERK = "AR_CLERK";
    public static final String AR_CONTROLLER = "AR_CONTROLLER";
    public static final String AR_AUDITOR = "AR_AUDITOR";
    public static final String TENANT_ADMIN = "TENANT_ADMIN";

    /** Roles implied for machine-to-machine API keys. */
    public static final Set<String> M2M_ROLES = Set.of(
            AR_CLERK, AR_CONTROLLER, AR_AUDITOR, TENANT_ADMIN);

    private ArRoles() {}
}