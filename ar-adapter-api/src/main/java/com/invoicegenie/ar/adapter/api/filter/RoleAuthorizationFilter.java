package com.invoicegenie.ar.adapter.api.filter;

import com.invoicegenie.ar.adapter.api.dto.ErrorResponse;
import com.invoicegenie.ar.adapter.api.security.ArRoles;
import com.invoicegenie.ar.adapter.api.security.RequireRoles;
import com.invoicegenie.ar.adapter.api.security.SecurityConstants;
import jakarta.ws.rs.Priorities;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.container.ContainerRequestFilter;
import jakarta.ws.rs.container.ResourceInfo;
import jakarta.ws.rs.core.Context;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.Provider;
import java.io.IOException;
import java.lang.reflect.Method;
import java.util.LinkedHashSet;
import java.util.Locale;
import java.util.Set;
import org.eclipse.microprofile.config.inject.ConfigProperty;

/**
 * Enforces {@link RequireRoles} (resource-level) and path-based defaults when security is enabled.
 * Phase-1 RBAC (STORY-003): AR_CLERK / AR_CONTROLLER / AR_AUDITOR / TENANT_ADMIN.
 */
@Provider
@jakarta.annotation.Priority(Priorities.AUTHORIZATION + 10)
public class RoleAuthorizationFilter implements ContainerRequestFilter {

    @ConfigProperty(name = "invoicegenie.security.enabled", defaultValue = "false")
    boolean securityEnabled;

    @Context
    ResourceInfo resourceInfo;

    @Override
    public void filter(ContainerRequestContext ctx) throws IOException {
        if (!securityEnabled) {
            return;
        }

        @SuppressWarnings("unchecked")
        Set<String> roles = (Set<String>) ctx.getProperty(SecurityConstants.AUTH_ROLES_PROPERTY);
        if (roles == null) {
            roles = Set.of();
        }

        Set<String> required = resolveAnnotationRoles();
        if (required.isEmpty()) {
            required = resolvePathRoles(ctx);
        }
        if (required.isEmpty()) {
            return;
        }
        boolean allowed = false;
        for (String r : required) {
            if (roles.contains(r)) {
                allowed = true;
                break;
            }
        }
        if (!allowed) {
            ctx.abortWith(Response.status(Response.Status.FORBIDDEN)
                    .entity(new ErrorResponse("FORBIDDEN",
                            "Missing required role(s): " + String.join(", ", required)))
                    .build());
        }
    }

    private Set<String> resolveAnnotationRoles() {
        Set<String> required = new LinkedHashSet<>();
        if (resourceInfo == null) {
            return required;
        }
        Method method = resourceInfo.getResourceMethod();
        if (method != null) {
            RequireRoles ann = method.getAnnotation(RequireRoles.class);
            if (ann != null) {
                for (String r : ann.value()) {
                    if (r != null && !r.isBlank()) {
                        required.add(r.trim().toUpperCase(Locale.ROOT));
                    }
                }
            }
        }
        if (required.isEmpty() && resourceInfo.getResourceClass() != null) {
            RequireRoles ann = resourceInfo.getResourceClass().getAnnotation(RequireRoles.class);
            if (ann != null) {
                for (String r : ann.value()) {
                    if (r != null && !r.isBlank()) {
                        required.add(r.trim().toUpperCase(Locale.ROOT));
                    }
                }
            }
        }
        return required;
    }

    private Set<String> resolvePathRoles(ContainerRequestContext ctx) {
        String method = ctx.getMethod() != null ? ctx.getMethod().toUpperCase(Locale.ROOT) : "GET";
        String path = normalize(ctx.getUriInfo() != null ? ctx.getUriInfo().getPath() : "");

        if (path.startsWith("/api/v1/auth")) {
            return Set.of();
        }

        if ("GET".equals(method) || "HEAD".equals(method) || "OPTIONS".equals(method)) {
            if (path.startsWith("/api/v1/audit")) {
                return Set.of(ArRoles.AR_AUDITOR, ArRoles.TENANT_ADMIN, ArRoles.AR_CONTROLLER);
            }
            return Set.of();
        }

        if (path.startsWith("/api/v1/tenants") || path.startsWith("/api/v1/webhooks")) {
            return Set.of(ArRoles.TENANT_ADMIN);
        }
        if (path.contains("/writeoff") || path.contains("/reverse") || path.contains("/refund")) {
            return Set.of(ArRoles.AR_CONTROLLER, ArRoles.TENANT_ADMIN);
        }
        if (path.startsWith("/api/v1/")) {
            return Set.of(ArRoles.AR_CLERK, ArRoles.AR_CONTROLLER, ArRoles.TENANT_ADMIN);
        }
        return Set.of();
    }

    private static String normalize(String path) {
        if (path == null || path.isBlank()) {
            return "/";
        }
        String p = path.startsWith("/") ? path : "/" + path;
        if (p.length() > 1 && p.endsWith("/")) {
            p = p.substring(0, p.length() - 1);
        }
        return p;
    }
}