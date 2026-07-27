package com.invoicegenie.ar.adapter.api.filter;

import com.invoicegenie.ar.adapter.api.dto.ErrorResponse;
import com.invoicegenie.ar.adapter.api.security.ApiKeyRegistry;
import com.invoicegenie.ar.adapter.api.security.ArRoles;
import com.invoicegenie.ar.adapter.api.security.SecurityConstants;
import com.invoicegenie.shared.tenant.ActorContext;
import jakarta.annotation.PostConstruct;
import jakarta.ws.rs.Priorities;
import jakarta.ws.rs.container.ContainerRequestContext;
import jakarta.ws.rs.container.ContainerRequestFilter;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.Provider;
import java.io.IOException;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import org.eclipse.microprofile.config.inject.ConfigProperty;
import org.jboss.logging.Logger;

/**
 * Optional authentication gate for production.
 *
 * <p>When {@code invoicegenie.security.enabled=true}:
 * <ul>
 *   <li>{@code api-key} mode - require {@code X-API-Key} (or Bearer token)
 *       mapped via {@code invoicegenie.security.api-keys=key:tenantUuid,...}</li>
 *   <li>{@code jwt} mode - require HS256 JWT (Bearer) with {@code tenant_id} claim,
 *       optional {@code roles[]} claim, signed with {@code invoicegenie.security.jwt.secret}</li>
 *   <li>{@code hybrid} mode - accept either Bearer JWT or {@code X-API-Key} (web + M2M)</li>
 * </ul>
 *
 * <p>Public paths (health) always bypass. OpenAPI/Swagger bypass only when
 * {@code invoicegenie.security.allow-openapi=true}. API keys map to full M2M roles;
 * JWT subjects receive roles from the claim (default {@code AR_CLERK} if empty).
 */
@Provider
@jakarta.annotation.Priority(Priorities.AUTHENTICATION)
public class AuthFilter implements ContainerRequestFilter {

    private static final Logger LOG = Logger.getLogger(AuthFilter.class);

    private static final Set<String> ALWAYS_PUBLIC_PREFIXES = Set.of(
            "/q/health",
            "/q/health/live",
            "/q/health/ready"
    );

    @ConfigProperty(name = "invoicegenie.security.enabled", defaultValue = "false")
    boolean securityEnabled;

    @ConfigProperty(name = "invoicegenie.security.mode", defaultValue = "api-key")
    String mode;

    /** Use "none" instead of empty string — SmallRye rejects empty defaults as null. */
    @ConfigProperty(name = "invoicegenie.security.api-keys", defaultValue = "none")
    String apiKeysConfig;

    @ConfigProperty(name = "invoicegenie.security.jwt.secret", defaultValue = "none")
    String jwtSecret;

    @ConfigProperty(name = "invoicegenie.security.allow-openapi", defaultValue = "true")
    boolean allowOpenApi;

    private ApiKeyRegistry apiKeyRegistry = new ApiKeyRegistry("");

    @PostConstruct
    void init() {
        apiKeyRegistry = new ApiKeyRegistry(normalizeConfig(apiKeysConfig));
        if (securityEnabled) {
            LOG.infof("API security enabled (mode=%s, apiKeys=%d)", mode, apiKeyRegistry.size());
        }
    }

    @Override
    public void filter(ContainerRequestContext requestContext) throws IOException {
        if (!securityEnabled) {
            // Still bind actor for audit (STORY-012) when security is off
            bindAnonymousActor(requestContext);
            return;
        }
        String path = normalizePath(requestContext.getUriInfo().getPath());
        if (isPublic(path)) {
            bindAnonymousActor(requestContext);
            return;
        }

        String modeNorm = mode == null ? "api-key" : mode.trim().toLowerCase(Locale.ROOT);
        Optional<AuthResult> auth = switch (modeNorm) {
            case "jwt" -> authenticateJwt(requestContext);
            case "api-key" -> authenticateApiKey(requestContext);
            case "hybrid", "api-key-or-jwt", "both" -> authenticateHybrid(requestContext);
            default -> {
                LOG.warnf("Unknown security mode '%s' - rejecting request", mode);
                yield Optional.empty();
            }
        };

        if (auth.isEmpty()) {
            requestContext.abortWith(Response.status(Response.Status.UNAUTHORIZED)
                    .entity(new ErrorResponse("UNAUTHORIZED", "Valid API credentials are required"))
                    .build());
            return;
        }

        AuthResult result = auth.get();
        requestContext.setProperty(SecurityConstants.AUTH_TENANT_PROPERTY, result.tenantId());
        requestContext.setProperty(SecurityConstants.AUTH_SUBJECT_PROPERTY, result.subject());
        requestContext.setProperty(SecurityConstants.AUTH_METHOD_PROPERTY, result.method());
        requestContext.setProperty(SecurityConstants.AUTH_ROLES_PROPERTY, result.roles());

        // STORY-012: bind actor for audit writers
        String ip = clientIp(requestContext);
        String ua = requestContext.getHeaderString("User-Agent");
        String actorType = "jwt".equals(result.method()) ? "USER" : "API";
        ActorContext.set(ActorContext.Actor.of(result.subject(), actorType, ip, ua));
    }

    private Optional<AuthResult> authenticateHybrid(ContainerRequestContext ctx) {
        Optional<AuthResult> jwt = authenticateJwt(ctx);
        if (jwt.isPresent()) {
            return jwt;
        }
        return authenticateApiKey(ctx);
    }

    private Optional<AuthResult> authenticateApiKey(ContainerRequestContext ctx) {
        String key = ctx.getHeaderString(SecurityConstants.HEADER_API_KEY);
        if (key == null || key.isBlank()) {
            String auth = ctx.getHeaderString(SecurityConstants.HEADER_AUTHORIZATION);
            if (auth != null && auth.toLowerCase(Locale.ROOT).startsWith("bearer ")) {
                key = auth.substring(7).trim();
            }
        }
        return apiKeyRegistry.resolveTenant(key)
                .map(tenant -> new AuthResult(tenant, "api-key", "api-key", ArRoles.M2M_ROLES));
    }

    private Optional<AuthResult> authenticateJwt(ContainerRequestContext ctx) {
        String auth = ctx.getHeaderString(SecurityConstants.HEADER_AUTHORIZATION);
        if (auth == null || auth.isBlank()) {
            return Optional.empty();
        }
        return ApiKeyRegistry.validateHs256Jwt(auth, normalizeConfig(jwtSecret))
                .map(c -> {
                    Set<String> roles = c.roles();
                    if (roles == null || roles.isEmpty()) {
                        roles = Set.of(ArRoles.AR_CLERK);
                    }
                    return new AuthResult(c.tenantId(), c.subject(), "jwt", roles);
                });
    }

    private static String clientIp(ContainerRequestContext ctx) {
        String xff = ctx.getHeaderString("X-Forwarded-For");
        if (xff != null && !xff.isBlank()) {
            int comma = xff.indexOf(',');
            return (comma > 0 ? xff.substring(0, comma) : xff).trim();
        }
        String realIp = ctx.getHeaderString("X-Real-IP");
        if (realIp != null && !realIp.isBlank()) {
            return realIp.trim();
        }
        return null;
    }

    private static void bindAnonymousActor(ContainerRequestContext ctx) {
        String ip = clientIp(ctx);
        String ua = ctx.getHeaderString("User-Agent");
        ActorContext.set(ActorContext.Actor.of("anonymous", "SYSTEM", ip, ua));
    }

    private boolean isPublic(String path) {
        for (String prefix : ALWAYS_PUBLIC_PREFIXES) {
            if (path.equals(prefix) || path.startsWith(prefix + "/")) {
                return true;
            }
        }
        // Public auth endpoints (login + refresh). logout/me require a valid access token.
        if (path.equals("/api/v1/auth/login") || path.startsWith("/api/v1/auth/login/")
                || path.equals("/api/v1/auth/refresh") || path.startsWith("/api/v1/auth/refresh/")) {
            return true;
        }
        // PP-011: public unsubscribe (tokenized, rate-limited)
        if (path.equals("/api/v1/public/unsubscribe") || path.startsWith("/api/v1/public/unsubscribe/")
                || path.equals("/api/v1/notifications/unsubscribe")
                || path.startsWith("/api/v1/notifications/unsubscribe/")) {
            return true;
        }
        if (allowOpenApi && (path.startsWith("/q/swagger")
                || path.startsWith("/q/openapi")
                || path.startsWith("/q/dev"))) {
            return true;
        }
        return false;
    }

    private static String normalizePath(String path) {
        if (path == null || path.isBlank()) {
            return "/";
        }
        String p = path.startsWith("/") ? path : "/" + path;
        if (p.length() > 1 && p.endsWith("/")) {
            p = p.substring(0, p.length() - 1);
        }
        return p;
    }

    private static String normalizeConfig(String value) {
        if (value == null) {
            return "";
        }
        String v = value.trim();
        if (v.isEmpty() || "none".equalsIgnoreCase(v) || "-".equals(v)) {
            return "";
        }
        return v;
    }

    private record AuthResult(String tenantId, String subject, String method, Set<String> roles) {}
}